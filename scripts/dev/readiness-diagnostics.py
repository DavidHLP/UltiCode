#!/usr/bin/env python3
"""Bounded loopback-only diagnostics; emit fixed enums, never response/log text."""
import http.client
import json
import sys

LIMIT = 8192
TARGETS = {
    "ulticode-auth": (9101, "/api/v1/auth/health/ready", ("db", "redis")),
    "ulticode-admin": (9102, "/api/v1/admin/health/ready", ("db", "redis", "problemOwnerRpc", "storage")),
    "ulticode-app": (9103, "/api/v1/app/health/ready", ("db", "redis", "storage")),
    "ulticode-notification": (9105, "/api/v1/notification/health/ready", ("db", "redis")),
}


def classify(status, body, keys):
    components = dict.fromkeys(keys, "unknown")
    if len(body) > LIMIT:
        return "oversized_response", components
    if status not in (200, 503):
        return "http_other", components
    try:
        document = json.loads(body)
    except (ValueError, UnicodeError):
        return "invalid_response", components
    if not isinstance(document, dict) or not isinstance(document.get("components"), dict):
        return "invalid_response", components
    for key in keys:
        value = document["components"].get(key)
        if value == "UP":
            components[key] = "up"
        elif value == "DOWN":
            components[key] = "down"
    return "http_200" if status == 200 else "http_503", components


def probe(app, connection_factory=http.client.HTTPConnection):
    port, path, keys = TARGETS[app]
    result, components = "unreachable", dict.fromkeys(keys, "unknown")
    connection = None
    try:
        # No ambient proxy, redirects, authentication or user-controlled URL.
        connection = connection_factory("127.0.0.1", port, timeout=5)
        connection.request("GET", path)
        response = connection.getresponse()
        result, components = classify(response.status, response.read(LIMIT + 1), keys)
    except (OSError, http.client.HTTPException, ValueError):
        pass
    finally:
        if connection is not None:
            try:
                connection.close()
            except OSError:
                pass
    return result, components


def main(argv):
    selected = set(argv[0].split(",")) if len(argv) == 1 else set()
    for app in TARGETS:
        if app not in selected:
            continue
        result, components = probe(app)
        print(f"ULTICODE_FINAL_HEALTH app={app} state={result}")
        for key, value in components.items():
            print(f"ULTICODE_FINAL_COMPONENT app={app} component={key} state={value}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
