---
description: "Runtime configuration"
globs:
  - ".env.example"
  - "docker/docker-compose*.yml"
  - "docker/**/*"
  - "ecosystem.config.cjs"
  - "scripts/dev/**/*"
  - "init-db/scripts/**/*"
---

# Runtime configuration

- Use the supported entry points in `scripts/dev/` and the relevant operations guide. Follow root security rules for secrets, exposed ports and readiness.
- Trace changed configuration to its actual consumers. Validate affected development and production Compose combinations when changing Compose.
- For sandbox changes, verify the affected build/runtime path and isolation behavior. Do not treat a successful image build as execution proof.
- Destructive data operations and external deployment need authorization for the target and scope; reuse authorization already given. Keep runtime and migration credentials separate.
