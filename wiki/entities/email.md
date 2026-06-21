---
title: Email
type: entity
tags: [email, communication]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/email/
  - init-db/migrations/V20260322__Create_Email_Tables.sql
aliases: [邮件]
---

# Email

Outbound email channel: templates, send logs, verification codes. A sibling
transport to in-app [[entities/notification]] — one event may fan out to both.

## Key tables

- `email_templates` — templated bodies (`autoResultMap`).
- `email_logs` — per-send audit (to, template, status, error).

## Controllers

- `EmailController` → `/email` (verification, opt-in sends).

## Flow

Event → resolve template → render → send → log to `email_logs`. Failures recorded
with error detail for retry/diagnostics.

## Source files

- `backend-spring/.../modules/email/` (controller, service/impl, entity, dto, mapper).
- `init-db/migrations/V20260322__Create_Email_Tables.sql` (baseline).

## Cross-links

- [[entities/notification]] · [[entities/user]] (verification)
- [[overview/backend-modules-overview]]

## Gotchas

- `email_templates` uses `autoResultMap`; preserve mapper config on edits.
- Verification codes are single-use + short TTL — never log them in `email_logs` body.
