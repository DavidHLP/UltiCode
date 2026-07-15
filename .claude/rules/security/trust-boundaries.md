---
paths:
  - "backend-spring/src/main/java/**/controller/**/*.java"
  - "backend-spring/src/main/java/**/security/**/*.java"
  - "backend-spring/src/main/java/com/ulticode/common/{annotation,aspect,audit}/**/*.java"
  - "backend-spring/src/main/java/com/ulticode/modules/{auth,user,admin}/**/*.java"
  - "backend-spring/src/main/java/com/ulticode/modules/websocket/**/*.java"
  - "backend-spring/src/main/resources/application*.{yml,yaml,properties}"
  - "console/src/**/*.{ts,vue}"
  - "management/src/**/*.{ts,vue}"
  - "shared/{auth-core,auth-ui,http-client,markdown-utils,theme}/**/*.{ts,vue,js}"
  - "docker-compose*.yml"
  - "docker/**/*.{yml,yaml,json,conf}"
---

# Trust-boundary review

- Read the root Security invariants before editing and list the specific invariants touched by the change.
- Map untrusted inputs, authenticated identity, authorization decisions, sensitive outputs, and persistence or transport boundaries before choosing the fix.
- Trace the full credential or content path with graph tools and literal searches; include framework configuration and non-code consumers that static calls miss.
- Derive negative test cases from the mapped boundaries and the root invariants, then run the relevant subtree checks.
- Review the final diff for alternate credential paths, bypasses, unsafe sinks, secret exposure, and configuration differences across environments.
