# Migration Decisions

Architectural decisions (ADR) taken during migration execution.
Each entry must reference the guide section(s) it interprets.

## ADR-MIG-INV (Migration-only table inventory)

Context:
Guide §5.1 requires inventorying migration-only tables (e.g. `views`,
`virtual_contest_sessions`, `forum_community_*`, `system_announcement_*`,
`submission_statuses`, `contest_rankings`, `contest_analytics`,
`DailyRecommendation`, `email_templates` (bootstrap), `email_logs` (bootstrap))
before any DROP.

Decision:
Phase 0 produces an inventory document in DECISIONS.md listing each
candidate's purpose, current Producer/Consumer, and the future R/I/Q
classification. No DROP is performed in Phase 0.

Alternatives:
- DROP now and rely on backups — rejected (data loss risk; production not
  inspected).
- Keep tables and continue — accepted default until evidence supports either
  retire or formalize.

Consequences:
- Phase 7 has a documented basis for archival.
- Operators can flag required tables early.

Affected Tasks:
- P0-SCHEMA-003

## ADR-MIG-JUDGE (Judge outbox/fence/stream cutover design)

Context:
Guide §7.1 and §8.3 require planning the cutover from Legacy Redisson RQueue
to the existing `judge_outbox` + generation/attempt fence + Redis Streams
`JudgeQueue` port. Phase 0 produces the design only.

Decision:
- `judge_outbox` stays as the canonical "send to judge" outbox
- Add a sibling `submission_result_outbox` (Phase 6 implementation) for
  post-verdict effects (Contest/Notification/Achievement)
- Generation fence on `submission.generation`; lease expiry reaper is the
  authoritative reclaimer
- Legacy `RQueue` dual-write remains behind a feature flag until canary
  validates the new path for one full business cycle

Alternatives:
- Replace `RQueue` immediately with Kafka/RocketMQ — rejected (overkill;
  guide §11.1 marks RocketMQ as future, conditional on Redis Streams
  capacity/SLA breach).
- Drop `RQueue` before canary — rejected (violates §8.3 dual-write window).

Consequences:
- Phase 0 produces a written design; implementation belongs to Phase 6.
- Result outbox separates "send" from "verdict delivered", fixing the gap
  flagged in §8.3.

Affected Tasks:
- P0-JUDGE-001, P6-OUTBOX-001, P6-RESULT-001

## ADR-MIG-OAUTH-COOKIE (OAuth state cookie binding)

Context:
Guide §7.1: OAuth state must be bound to an HttpOnly cookie and compared
in the callback, in addition to the Redis check.

Decision:
- `OAuthStateModule.issueState` already sets `oauth_state_<provider>`
  cookie with HttpOnly; same cookie name is read in `validateAndConsume`.
- Constant-time comparison is implemented via `MessageDigest.isEqual` on
  the cookie vs callback state before the Redis getAndDelete.
- Mismatch throws BusinessException(UNAUTHORIZED) and clears the cookie.

Alternatives:
- Keep Redis-only check — rejected (defeats CSRF protection goal).
- Use signed state JWT — rejected (extra crypto dependency without clear
  benefit beyond the current random UUID approach).

Consequences:
- Adds a small constant-time comparison to the callback hot path.
- Requires reading the request cookies in OAuthService; controller must
  pass them through.

Affected Tasks:
- P0-SEC-001

## ADR-MIG-DOMAIN (No Course/Teacher/Student services)

Context:
Guide §1.1/2.1: There is no LMS domain in current code. Premature service
splits for hypothetical educational domains are explicitly excluded.

Decision:
Migration does not introduce Course/Classroom/Enrollment/Teacher/Student
entities, services, or routes. Only USER/MODERATOR/ADMIN/SUPER_ADMIN roles
remain.

Alternatives:
- Speculatively scaffold LMS modules — rejected (guide §1.1).

Consequences:
- Later, if LMS appears, modeling begins from a fresh domain study.

Affected Tasks:
- (No Phase-0 task; this is a project-wide guard.)

## ADR-MIG-MQ (RocketMQ deferred)

Context:
Guide §11.1 marks RocketMQ as future-only, contingent on Redis Streams
reaching a documented SLA / capacity ceiling.

Decision:
Phase 6 builds on Redis Streams integration bus, reusing existing
infrastructure. RocketMQ is not introduced.

Alternatives:
- Adopt RocketMQ now — rejected (cost > benefit).

Consequences:
- Requires re-evaluation when backlog or replay demands grow.

Affected Tasks:
- P6-OUTBOX-001, P6-INBOX-001

## ADR-MIG-LEGACY-KEEP (backend-legacy shell preserved until Phase 4)

Context:
Guide §Phase-1 requires the Maven reactor to allow Legacy to keep building.

Decision:
The single-module sources move under `backend-legacy/` during Phase 1;
all routes still go to that module. The Gateway uses Legacy as default
upstream until Phase 4 begins cutover.

Alternatives:
- Big-bang move — rejected (guide §1.3 forbids).

Consequences:
- The current codebase keeps building until Phase 4. Code reorganization
  is additive.

Affected Tasks:
- P1-INFRA-001, P4-CUTOVER-001..003, P7-LEGACY-001

## ADR-MIG-WS-COOKIE (WebSocket token must come from cookie)

Context:
Guide §7.7: WS CONNECT only accepts `access_token` cookie; no query, URL,
or client-controlled STOMP token.

Decision:
Handshake interceptor extracts from cookie only. STOMP CONNECT headers are
treated as a fallback only when the cookie path is unavailable in tests.
A single JWT validator (HMAC for compat window) is shared between HTTP
filter and WS authenticator.

Alternatives:
- Accept query token for ease of debugging — rejected (security risk).

Consequences:
- Blocks ad-hoc WS clients without cookie support.

Affected Tasks:
- P0-SEC-003, P2-AUTH-002