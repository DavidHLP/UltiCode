---
paths:
  - "backend-spring/src/**/*.{java,yml,yaml,properties}"
---

# Spring review entry point

- Use `08-java-code-review-checklist.md` for the review workflow.
- Treat `springboot-rules.md` as the single source for Spring proxy, transaction, async, scheduling, configuration, and wiring constraints.
- Cite the violated rule and changed location; do not copy the Spring rule text into the review finding.
- Require an application-context or focused Spring test only when plain unit tests cannot prove the changed wiring or proxy behavior.
