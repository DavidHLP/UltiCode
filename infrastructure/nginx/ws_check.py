#!/usr/bin/env python3
"""Tiny check for the WS location hardening.

Usage:
  python3 ws_check.py <nginx.conf> <location-path>

Reads the conf file, finds the `location <location-path> { ... }` block,
and verifies that the body contains `proxy_buffering off`, `proxy_cache
off`, and `proxy_read_timeout 86400`. Prints "OK" on success or "FAIL
<missing-list>" otherwise.
"""
import re
import sys


def main():
    if len(sys.argv) != 3:
        print("FAIL usage")
        sys.exit(1)
    path, family = sys.argv[1], sys.argv[2]
    text = open(path).read()
    pattern = (
        r"^[ \t]*location[ \t]+"
        + re.escape(family)
        + r"[^\n]*\{"
    )
    m = re.search(pattern, text, re.MULTILINE)
    if not m:
        print("FAIL no-such-location")
        sys.exit(0)
    depth = 0
    i = m.end() - 1
    while i < len(text):
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                break
        i += 1
    body = text[m.end():i]
    required = ["proxy_buffering off", "proxy_cache off", "proxy_read_timeout 86400"]
    missing = [r for r in required if r not in body]
    if missing:
        print("FAIL " + " ".join(missing))
    else:
        print("OK")


if __name__ == "__main__":
    main()
