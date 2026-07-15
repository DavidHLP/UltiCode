---
paths:
  - ".env.example"
  - "docker-compose*.yml"
  - "docker/**/*"
  - "ecosystem.config.cjs"
  - "scripts/dev/**/*"
---

# Runtime and infrastructure workflow

- Inspect the base, development, and production configurations together before changing Compose, environment variables, ports, health checks, startup order, or service dependencies.
- Preserve the security and readiness invariants in the root `AGENTS.md`. Development exposure must be explicit and must not leak into base or production configuration.
- Treat `.env.example` as a schema with safe placeholders, not as a source of working credentials. Keep secret names aligned across Compose, scripts, and application configuration.
- For judge sandbox changes, trace source files through the harness build or staging step into the image and verify the runtime version and isolation assumptions inside the image.
- Prefer the supported scripts in `scripts/dev/` over ad hoc operational commands. Keep failures actionable and avoid readiness probes for endpoints the application does not expose.
- Validate both development and production Compose merges, run the affected script's safe check mode when available, and inspect generated changes before completion.
