---
paths:
  - "backend-spring/src/main/java/**/controller/**/*.java"
  - "backend-spring/src/main/java/**/security/**/*.java"
  - "backend-spring/src/main/java/com/ulticode/modules/{auth,user,admin}/**/*.java"
  - "backend-spring/src/main/resources/application*.{yml,yaml,properties}"
  - "console/src/**/*.{ts,vue}"
  - "management/src/**/*.{ts,vue}"
  - "shared/{auth-core,auth-ui,http-client,markdown-utils,theme}/**/*.{ts,vue,js}"
  - "docker-compose*.yml"
  - "docker/**/*.{yml,yaml,json,conf}"
---

# Trust-boundary review

- Re-read the root security invariants before changing authentication, authorization, tokens, cookies, WebSockets, audit identity, rendering, URLs, infrastructure exposure, or secrets.
- Identify the untrusted input, authenticated principal, authorization decision, sensitive output, and persistence or transport boundary before editing.
- Enforce access control on the backend even when the frontend hides an action. Derive actor identity from the authenticated context rather than request-controlled data.
- Preserve the established cookie, token rotation, OAuth state, CSRF, and WebSocket authentication flows end to end; do not introduce a second credential path for convenience.
- Route Markdown and KaTeX through the shared sanitization pipeline, and treat URL handling plus any HTML-rendering sink as hostile-input code.
- Keep secrets out of source, logs, fixtures, migrations, and command output. Preserve production network isolation in Compose and deployment configuration.
- Add negative tests for unauthenticated, unauthorized, stale or replayed credentials, malformed input, and malicious rendering payloads as relevant.
