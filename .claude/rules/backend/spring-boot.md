---
paths:
  - "services/**/*.java"
  - "services/**/pom.xml"
  - "services/**/src/**/*.yml"
  - "services/**/src/**/*.yaml"
  - "services/**/src/**/*.properties"
  - "docker/sandbox/harness/java/src/**/*.java"
---

# Backend changes

- For services, read `services/AGENTS.md` and the nearest module guide. Use the Java level and dependencies configured by the affected build.
- Keep authorization tied to the authenticated principal and target resource. Validate untrusted paths, URLs and command arguments at their boundary; fail closed and test rejected inputs when changing security behavior.
- Keep transaction ownership in the service operation. Check Spring proxy entry when changing transactional, async or method-security behavior; avoid holding transactions across remote calls or sandbox execution.
- Preserve causes, interruption and resource cleanup on changed failure paths. Do not log credentials or sensitive payloads.
- For concurrent or retried operations, preserve atomic updates, idempotency and bounded work. Add a focused regression for the risk changed; use integration or Spring tests when mocks cannot establish the contract.
