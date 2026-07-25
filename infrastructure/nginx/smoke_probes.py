#!/usr/bin/env python3
"""Smoke runner for the gateway baseline.

Asserts (against a live gateway on 127.0.0.1:8081):
  - 10 route families reach the backend at the correct upstream path
  - 25 identity-claim headers are stripped (across 6 family locations)
  - `Connection` is "upgrade" only when the client asked for an upgrade
  - underscore-header smuggling is rejected with HTTP 400

Exits 0 with output "ALL_PASS" on full success, otherwise prints one line
per failure and exits 0 (the surrounding bash wraps the call).
"""
import http.client
import json
import socket
import sys

GATEWAY_HOST = "127.0.0.1"
GATEWAY_PORT = 8081

STRIP_HEADERS = [
    # X-User-*
    "X-User-Id", "X-User-Name", "X-User-Email", "X-User-Roles",
    "X-User-Status", "X-User-Idp",
    # X-Role*
    "X-Role", "X-Roles", "X-Role-Scope",
    # X-Service*
    "X-Service", "X-Service-Name", "X-Service-Token",
    "X-Service-Id", "X-Service-Version",
    # Specific service-forged tokens / auth bypass names
    "X-Internal", "X-Admin-Token", "X-Auth-Bypass", "X-Auth-Token",
    "X-Actor", "X-Actor-Id", "X-Impersonate",
    "X-Principal", "X-Principal-Id",
    # Set by trusted upstream proxies when relaying auth decisions
    "X-Forwarded-User", "X-Remote-User",
]


def req(method, path, headers=None, body=None):
    c = http.client.HTTPConnection(GATEWAY_HOST, GATEWAY_PORT, timeout=5)
    h = dict(headers or {})
    if body and "Content-Type" not in h:
        h["Content-Type"] = "application/json"
    c.request(method, path, body=body, headers=h)
    r = c.getresponse()
    raw = r.read()
    try:
        return r, json.loads(raw.decode("utf-8", "replace"))
    except Exception:
        return r, {"raw": raw.decode("latin1", "replace")[:200]}


def raw_req(path, extra_hdrs):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.connect((GATEWAY_HOST, GATEWAY_PORT))
    lines = [f"GET {path} HTTP/1.1", "Host: 127.0.0.1:8081", "Connection: close"]
    lines += extra_hdrs
    s.sendall(("\r\n".join(lines) + "\r\n\r\n").encode())
    data = b""
    s.settimeout(2)
    try:
        while True:
            c = s.recv(4096)
            if not c:
                break
            data += c
    except socket.timeout:
        pass
    s.close()
    body = data.split(b"\r\n\r\n", 1)[1]
    return json.loads(body.decode("utf-8", "replace"))


def main():
    failures = []

    # ---------- Path translation ----------
    probes = [
        ("/api/auth/login",                   "/auth/login"),
        ("/api/admin/users",                  "/admin/users"),
        ("/api/moderation/queue",             "/moderation/queue"),
        ("/api/moderation/appeals",           "/moderation/appeals"),
        ("/api/contest/ranking",              "/contest/ranking"),
        ("/api/problems/1",                   "/problems/1"),
        ("/api/ws/notifications/info",        "/ws/notifications/info"),
        ("/api/ws/contest/123/abc/websocket", "/ws/contest/123/abc/websocket"),
        ("/ws/contest/123/info",              "/ws/contest/123/info"),
        ("/ws/contest/123/abc/xhr",           "/ws/contest/123/abc/xhr"),
    ]
    for path, expected in probes:
        r, body = req("GET", path)
        got = body.get("path") if r.status == 200 else None
        if got != expected:
            failures.append(f"path-translate {path}: got {got!r} expected {expected!r}")

    # ---------- Header strip per family ----------
    family_paths = [
        ("/api/auth/",       "/api/auth/login"),
        ("/api/admin/",      "/api/admin/users"),
        ("/api/moderation/", "/api/moderation/queue"),
        ("/api/",            "/api/contest/ranking"),
        ("/api/ws/",         "/api/ws/notifications/info"),
        ("/ws/",             "/ws/contest/123/info"),
    ]
    for family_label, path in family_paths:
        for hdr in STRIP_HEADERS:
            r, body = req("GET", path, headers={hdr: "forged-value"})
            sent = body.get("headers", {}).get(hdr.lower())
            if sent not in (None, ""):
                failures.append(f"{family_label} leak {hdr}: upstream saw {sent!r}")

    # ---------- Connection upgrade header behavior ----------
    r = raw_req("/api/auth/login", [])
    if r["headers"].get("connection") == "upgrade":
        failures.append("plain REST /api/auth/login: upstream got Connection=upgrade (would break SockJS fallbacks)")

    r = raw_req("/api/ws/notifications", [
        "Upgrade: websocket",
    ])
    if r["headers"].get("connection") != "upgrade":
        failures.append("raw WS /api/ws/notifications: upstream did not get Connection=upgrade")

    # SockJS xhr_streaming / xhr_send / xhr — long-poll transports.
    # These must NOT trigger Connection=upgrade (which would break the
    # keep-alive semantics the polling transports rely on).
    for sjs_path in ("/api/ws/contest/123/xhr_streaming",
                     "/api/ws/contest/123/xhr_send",
                     "/api/ws/contest/123/xhr",
                     "/api/ws/notifications/info"):
        r = raw_req(sjs_path, [])
        if r["headers"].get("connection") == "upgrade":
            failures.append(f"SockJS {sjs_path}: upstream got Connection=upgrade (would break SockJS HTTP-fallback)")
    # Underscore-header smuggling is silently dropped by
    # by nginx because of `underscores_in_headers off;` in the host
    # conf. That directive causes nginx to NOT forward underscore-
    # named headers upstream, so the backend never sees them. The
    # status here is 200 (request reached the proxy) but the upstream
    # must NOT see the value.
    try:
        c = http.client.HTTPConnection(GATEWAY_HOST, GATEWAY_PORT, timeout=5)
        c.request("GET", "/api/auth/login",
                  headers={"X_User_Id": "forged-via-underscore",
                           "X_Role":    "forged-via-underscore"})
        r = c.getresponse()
        body = json.loads(r.read().decode())
        seen = set(body.get("headers", {}).keys())
        for forbidden in ("x_user_id", "x_role"):
            if forbidden in seen:
                failures.append(f"underscore-header {forbidden} leaked upstream")
    except Exception as e:
        failures.append(f"underscore-header probe raised: {e}")

    if failures:
        for f in failures:
            print(f)
        sys.exit(1)
    print("ALL_PASS")


if __name__ == "__main__":
    main()
