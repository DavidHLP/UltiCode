---
title: UltiCode Privacy & Log Retention
tags: [security, privacy, reference]
status: living
updated: 2026-06-18
owner: backend+ops
---

# UltiCode Privacy & Log Retention

> **作用**：UltiCode 项目用户生成数据保留与隐私治理基线。
> **创建**：2026-06-17（R10.9，F-SEC-13 收口）
| **关联**：[docs/contest/EXECUTION_PLAN_R10 §9](./contest/_archive/EXECUTION_PLAN_R10_2026-06-18.md) · [SECURITY_REVIEW F-SEC-13](./contest/_archive/SECURITY_REVIEW_2026-06-17.md) |
> **维护者**：后端 + 运维

## Scope

This document covers user-generated data retention, especially sensitive
contest-related data (virtual session records, AC/failure history, admin
audit trails). It is the canonical reference for retention period, storage
location, and deletion trigger for each data class.

## Log Retention

| Data Type | Retention | Storage | Deletion Trigger |
|-----------|-----------|---------|------------------|
| Application logs (INFO/WARN) | 90 days | `logs/ulticode-9001-*.log` (rotated daily) | Auto via logback `TimeBasedRollingPolicy` |
| Error stack traces (ERROR) | 180 days | Same as above | Auto + manual review for security incidents |
| Virtual session records | 180 days | MySQL `contest_participants` (is_virtual=1) | Manual `DELETE` after retention window |
| Submission code & test details | 180 days | MySQL `submissions` / `contest_submissions` | Manual `DELETE` after retention window |
| Admin audit log (scoring config changes, ban actions) | 365 days | MySQL `audit_log` (if exists) / structured application log | Manual |
| Authentication events (login/logout/failures) | 365 days | MySQL `auth_events` (if exists) / application log | Manual |
| WebSocket session events | 30 days | Application log | Auto via logback rotation |

## PII / Sensitive Fields

| Field | Treatment | Example |
|-------|-----------|---------|
| Email | Stored as-is, masked in user-facing views | `1****@foo.com` |
| Phone | Stored as-is, masked in user-facing views | `138****1234` |
| User IP | Stored in audit log, never exposed in user-facing API | (audit only) |
| Code submission | Stored 180 days, then purged | (full code) |
| Test details (judge output) | Stored 180 days, then purged | (full output) |

## GDPR / Data Subject Access

For data export / deletion requests, use `scripts/dev/data-subject.sh <userId>`
(**planned, not yet implemented** — see R10 follow-ups). The script will:

- Export: all user data from `users`, `submissions`, `contest_participants`,
  `notifications`, `audit_log`
- Delete: anonymize user record (replace email/phone with `deleted-{userId}@example.com`),
  retain submission data for anti-cheat audit but mask PII

## Virtual Session Audit Trail (F-SEC-13)

Current state of `finishVirtualContest` (ContestSchedulerServiceImpl) and
`autoFinishVirtualParticipants` (ContestScoringServiceImpl):

- **Logging**: Both methods emit `log.info(...)` with userId, contestId,
  participantId, finishedAt. These entries are searchable via the application
  log and retained per the "Application logs" row above.
- **Audit table**: **No dedicated `audit_log` table** for contest actions
  currently exists. This is **accepted as low risk** until product defines
  explicit anti-cheat requirements (e.g., "must be able to reconstruct
  every AC event for the last 365 days for dispute resolution").
- **Decision trigger**: If product requests a formal audit table, see R10
  follow-up: a new `contest_audit_log` table with
  `(actor, action, payload, created_at)` and a 365-day retention policy.

## Log Retention Configuration

### Application Logs (Logback)

`backend-spring/src/main/resources/logback-spring.xml` should define:

```xml
<appender name="ROLLING_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/ulticode-9001.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>logs/ulticode-9001-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
        <maxHistory>90</maxHistory>  <!-- 90 days retention -->
        <totalSizeCap>5GB</totalSizeCap>
    </rollingPolicy>
</appender>
```

For ERROR-level stacks, route to a separate appender with 180-day retention.

### Database Tables

The manual deletion scripts are intentionally not automated. Use
`scripts/dev/data-retention.sh` (**planned**) to run quarterly:

```sql
-- Virtual session cleanup (180 days)
DELETE FROM contest_participants
WHERE is_virtual = 1
  AND finished_at < NOW() - INTERVAL 180 DAY;

-- Submission code cleanup (180 days)
DELETE FROM submissions
WHERE created_at < NOW() - INTERVAL 180 DAY;
```

## R10 Note

This document is **new in R10.9** and was previously implied by
`SECURITY_REVIEW.md F-SEC-13` without a concrete home. It serves as the
canonical reference for log/data retention going forward. Any future feature
that introduces a new user-data table must update this document as part of
the PR checklist (enforced via CODEOWNERS for `docs/PRIVACY.md`).

## R10 Follow-ups (not in scope of R10.9)

- Implement `scripts/dev/data-subject.sh` (GDPR data export/deletion)
- Implement `scripts/dev/data-retention.sh` (quarterly cleanup)
- Add `contest_audit_log` table if product requires formal audit trail
- Add `audit_log` table for admin scoring config changes (currently
  documented in R10.8 migration audit log only)

---

## See also

- [README.md](./README.md) — 文档总入口
- [contest/SECURITY_REVIEW (归档)](./contest/_archive/SECURITY_REVIEW_2026-06-17.md) — Contest 安全专项审查（F-SEC-13 来源）
- [contest/EXECUTION_PLAN_R10 §9 (归档)](./contest/_archive/EXECUTION_PLAN_R10_2026-06-18.md) — 本文档的落地上下文
- `CLAUDE.md` → **Security Invariants**（仓库根）— 安全不变量权威
