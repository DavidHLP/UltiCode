---
paths:
  - "services/**/src/**/*.{java,yml,yaml,properties}"
kind: rules
summary: 'Spring-specific code review rules.'
---

# Spring review entry point

- Use `java-code-review-checklist.md` for the review workflow.
- Treat `backend/spring-boot.md` as the single source for Spring proxy, transaction, async, scheduling, configuration, and wiring constraints.
- Cite the violated rule and changed location; do not copy the Spring rule text into the review finding.
- Require an application-context or focused Spring test only when plain unit tests cannot prove the changed wiring or proxy behavior.
