---
kind: rules
paths:
  - '.env.example'
  - 'docker-compose*.yml'
  - 'docker/**/*'
  - 'ecosystem.config.cjs'
  - 'scripts/dev/**/*'
summary: 'Runtime and infrastructure rules (Docker, Compose, PM2).'
triggers:
  - 'docker'
  - 'deploy'
  - 'infrastructure'
  - 'compose'
  - 'pm2'
---
# Runtime and infrastructure workflow

- Read the root security and verification sections, then render the effective base-plus-development and base-plus-production configurations before editing.
- Trace each changed environment variable from its documented placeholder through Compose and scripts to every application consumer; classify whether it is configuration or a secret.
- For judge sandbox changes, trace source files through the harness build or staging step into the image and verify the runtime version and isolation assumptions inside the image.
- Exercise the supported workflow that owns the changed operation rather than validating isolated commands only.
- Run every configuration check required by the root guide and compare the rendered configurations before and after the change.
