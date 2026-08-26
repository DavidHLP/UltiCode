---
paths:
  - "services/**/src/main/java/**/controller/**/*.java"
  - "services/**/src/main/java/**/security/**/*.java"
  - "services/**/src/main/java/**/adapter/**/*.java"
  - "services/platform/common/src/main/java/com/ulticode/common/annotation/**/*.java"
  - "services/platform/common/src/main/java/com/ulticode/common/audit/**/*.java"
  - "services/platform/common/src/main/java/com/ulticode/common/util/AuditContext.java"
  - "services/platform/web-security/src/main/java/com/ulticode/audit/**/*.java"
  - "services/platform/web-security/src/main/java/com/ulticode/common/auth/**/*.java"
  - "services/platform/web-security/src/main/java/com/ulticode/websecurity/**/*.java"
  - "services/app/app-web/src/main/java/com/ulticode/modules/websocket/**/*.java"
  - "services/**/src/main/resources/application*.{yml,yaml,properties}"
  - "services/**/src/main/resources/security/serialize.allowlist"
  - "apps/console/src/**/*.{ts,vue}"
  - "apps/management/src/**/*.{ts,vue}"
  - "packages/{auth-core,auth-ui,http-client,markdown-utils,theme,design-system}/**/*.{ts,vue,js}"
  - "docker-compose*.yml"
  - "docker/**/*.{yml,yaml,json,conf}"
kind: rules
summary: 'Cross-cutting trust boundary and security invariants.'
---

# Trust-boundary review

- Read the root Security invariants before editing and list the specific invariants touched by the change.
- Map untrusted inputs, authenticated identity, authorization decisions, sensitive outputs, and persistence or transport boundaries before choosing the fix.
- Trace the full credential or content path with graph tools and literal searches; include framework configuration and non-code consumers that static calls miss.
- Derive negative test cases from the mapped boundaries and the root invariants, then run the relevant subtree checks.
- Review the final diff for alternate credential paths, bypasses, unsafe sinks, secret exposure, and configuration differences across environments.
- Invariants: Tokens in HttpOnly cookies; OAuth state consumed atomically; WebSocket auth via cookie only; `/admin/**` requires `ADMIN`/`SUPER_ADMIN` with principal-derived audit identity; Markdown rendering sanitized via `markdown-utils`; zero exposed infrastructure ports in base/prod Compose; no seed credentials in migrations.
