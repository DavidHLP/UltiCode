#!/usr/bin/env python3
"""Tiny HTTP echo backend used to verify nginx path translation.

It logs every request (method, path, query) and returns a JSON record of
what it received. It listens on 9001 by default. Used by tests in
scripts/test/gateway-baseline.sh.
"""
import http.server
import json
import sys
import threading


class EchoHandler(http.server.BaseHTTPRequestHandler):
    def _record(self, method):
        body_len = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(body_len) if body_len else b""
        record = {
            "method": method,
            "path": self.path,
            "headers": {k.lower(): v for k, v in self.headers.items()},
            "body_len": body_len,
            "body_text": body.decode("utf-8", "replace"),
        }
        payload = json.dumps(record, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        self._record("GET")

    def do_POST(self):
        self._record("POST")

    def do_OPTIONS(self):
        self.send_response(204)
        self.end_headers()

    def log_message(self, fmt, *args):
        sys.stderr.write("[echo-backend] " + (fmt % args) + "\n")


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9001
    server = http.server.ThreadingHTTPServer(("0.0.0.0", port), EchoHandler)
    print(f"[echo-backend] listening on 0.0.0.0:{port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
