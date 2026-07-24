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

### Inventory (Phase 0 sweep)

Source of truth: `init-db/migrations/V20260602_120000__Create_All_Tables.sql`
(repo-wide grep confirmed zero `@TableName(...)` references in any
current `src/main/java` entity for the tables below; they exist in the
schema but no Java code reads or writes them).

| Table | Migration line | Java ref | Disposition (Phase 7 candidate) |
|---|---|---|---|
| `DailyRecommendation` | 4 | none | R (verify production data; archive if zero; DROP only after one business cycle) |
| `contest_analytics` | 117 | none | R (current analytics computed in-memory; archive if zero) |
| `contest_rankings` | 215 | none | R (current ranking via participant + cache) |
| `forum_community_links` | 393 | none | R (verify FK; archive if zero) |
| `forum_community_permissions` | 415 | none | R (forum RBAC managed in `forum_community_members`; verify before DROP) |
| `forum_community_rules` | 427 | none | R |
| `forum_community_tags` | 438 | none | R |
| `forum_post_tag_relations` | 446 | none | R (forum tags live elsewhere) |
| `submission_statuses` | 910 | none | I (status enum is `SubmissionStatusCatalog` in code; table is a static reference set — keep as read-only seed; no writer) |
| `system_announcement_reads` | 971 | none | R (announcement feature unused in current code) |
| `system_announcements` | 982 | none | R (same as above) |
| `views` | 1134 | none | R (forum/solution view counts live as denormalized columns on `forum_posts` / `solutions`; the `views` table is shadow state — verify before DROP) |
| `virtual_contest_sessions` | 1146 | none | R (per the data ownership matrix; activity state already lives on `contest_participants`) |

Disambiguation (NOT migration-only — code DOES use these):
- `email_templates`, `email_logs` — used by `modules/email/**`. Owner:
  App (Notification).
- `problem_notes` — covered by P0-SCHEMA-002 migration convergence.
- `password_resets` — empty in current prod; the reset flow stores the
  hash on `users.password_reset_*`. R candidate.
- `system_settings` — used by `modules/admin/**`. Owner: Admin.

Future evidence required before any DROP:
- `SELECT COUNT(*)` and `MAX(created_at)` per table on production.
- Confirmation that no admin tooling / read replica / analytics pipeline
  reads the candidate tables.
- One full business cycle of canary without a write or read from these
  tables.

The above evidence is collected in Phase 7 (P7-DB-001). For now, all
candidates are flagged R in the matrix; no migration drops or alters them.

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

## ADR-MIG-WS-BLACKLIST (TokenBlacklistPort stays read-only in Phase 0)

Context:
Guide §7.1 mentions "access-token blacklist has reads but no complete
write chain in source." Inspection of `com.ulticode.modules.websocket.port.TokenBlacklistPort`
shows the port is deliberately read-only: a port-adapter audit removed the
unused `blacklistToken(...)` writers because runtime revocation is owned
by `RefreshTokenService` (DB-backed hash-only store, see
V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql). The
WS port's Javadoc explicitly directs future admin instant-revoke work to
add a separate writer port rather than widen the read port.

Decision:
Phase 0 does NOT add write methods to `TokenBlacklistPort`. The current
write chain for token revocation is `RefreshTokenService.revoke*` (DB
hash-only + CAS). An admin-driven access-token kill switch, if added
later, gets its own `TokenRevocationWritePort` in the auth module.

Alternatives:
- Widen `TokenBlacklistPort` to add `blacklist(token, ttl)` — rejected
  (violates the port's documented read-only contract; surfaces writes
  on the WS hot path; duplicate state).
- Keep dual write: `RefreshTokenService` + Redis — rejected (two stores
  drift; guide §8.3 mandates single Owner).

Consequences:
- Phase 0 /auth/logout still relies on `RefreshTokenService` for
  revocation; WS sessions for the killed refresh chain naturally expire
  at access-token TTL.
- An admin instant-revoke feature requires a separate design pass and
  Phase 4 RPC hand-off to auth.

Affected Tasks:
- P0-SEC-003 (the read side is unchanged; this ADR documents the
  explicit non-decision on the write side).
