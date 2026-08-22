---
name: security-trust-boundaries
description: Cross-cutting trust boundary and security invariants.
globs:
  - {backend-auth,backend-admin,backend-app}/src/main/java/**/controller/**/*.java
  - {backend-auth,backend-admin,backend-app}/src/main/java/**/security/**/*.java
  - {backend-auth,backend-admin,backend-app}/src/main/java/**/*.{java,yml,yaml,properties}
  - services/platform/common/src/main/java/com/ulticode/common/annotation/**/*.java
  - services/platform/common/src/main/java/com/ulticode/common/audit/**/*.java
  - services/platform/common/src/main/java/com/ulticode/common/util/AuditContext.java
  - services/platform/web-security/src/main/java/com/ulticode/audit/**/*.java
  - services/platform/web-security/src/main/java/com/ulticode/common/auth/**/*.java
  - services/platform/web-security/src/main/java/com/ulticode/websecurity/**/*.java
  - services/app/app-web/src/main/java/com/ulticode/modules/websocket/**/*.java
  - {backend-auth,backend-admin,backend-app}/src/main/resources/application*.{yml,yaml,properties}
  - services/**/src/main/resources/security/serialize.allowlist
  - apps/console/src/**/*.{ts,vue}
  - apps/management/src/**/*.{ts,vue}
  - packages/{auth-core,auth-ui,http-client,markdown-utils,theme}/**/*.{ts,vue,js}
  - docker-compose*.yml
  - docker/**/*.{yml,yaml,json,conf}
condition: ["(?i)Trust-boundary|review"]
interruptMode: never
alwaysApply: false
---

# Trust-boundary review

- Read the root Security invariants before editing and list the specific invariants touched by the change.
- Map untrusted inputs, authenticated identity, authorization decisions, sensitive outputs, and persistence or transport boundaries before choosing the fix.
- Trace the full credential or content path with graph tools and literal searches; include framework configuration and non-code consumers that static calls miss.
- Derive negative test cases from the mapped boundaries and the root invariants, then run the relevant subtree checks.
- Review the final diff for alternate credential paths, bypasses, unsafe sinks, secret exposure, and configuration differences across environments.
