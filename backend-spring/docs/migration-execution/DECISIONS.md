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
|-------|----------------|----------|---------------------------------|
| `views` | L645 | None | Retire: `problem_views` exists |
| `virtual_contest_sessions` | L703 | None | Formalize (Virtual Contest) |
| `forum_community_profiles` | L769 | None | Retire (no read path) |
| `forum_community_settings` | L773 | None | Retire (no read path) |
| `system_announcement_notifications` | L433 | None | Retire (notifications table exists) |
| `system_announcement_read_records` | L440 | None | Retire (no read path) |
| `submission_statuses` | L797 | None | Formalize (SubmissionStatus enum) |
| `contest_rankings` | L810 | None | Formalize (Rating) |
| `contest_analytics` | L818 | None | Formalize (Analytics) |
| `DailyRecommendation` | L826 | None | Formalize (Recommendation) |
| `email_templates` | L951 | None | Bootstrap only |
| `email_logs` | L956 | None | Bootstrap only |

## ADR-MIG-WS-BINDING (Cookie-bound OAuth state, WS JWT validation)

Context:
Guide §7.1 §12 R4,R6 require fixing OAuth state token exposure (R4) and
WebSocket authentication hard-coding to query/subscription tokens (R6).
Inspection of `OAuthStatePort.validateState` shows it accepted only the
`state` query parameter, making CSRF replay trivial. `JwtChannelInterceptor`
unconditionally trusted `StompHeader.ACCESS_TOKEN` header on CONNECT,
ignoring HttpOnly cookie JWT.

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

## ADR-MIG-JUDGE (Judge outbox/fence/stream cutover design)

Context:
Guide §7.1 §8.3 require:
- Eliminating legacy Redisson `RQueue` dual-write.
- Completing `judge_outbox` + generation fence + `JudgeQueue` Redis Streams
  cutover.
- Adding a result outbox (post-verdict) to avoid JVM crash losing
  Contest/Notification/Achievement updates.
- Feature-flag driven dual-write window with exit criteria for legacy
  deprecation.

Current state (inspection of codebase):
- `JudgeQueue` exists (Redis Streams) but is not the primary write path.
- `judge_outbox` table exists but only covers "submit to judge"; verdict
  handling does NOT use outbox (violates §8.3).
- No generation/attempt fence around submission intake.
- Legacy `RQueue` is still active; no controlled cutover path.

Decision:
Phase 0 produces a design document (this ADR) covering the complete
judge outbox/fence/stream architecture; implementation spans Phase 0-2.
No code changes in Phase 0 beyond design.

Architecture (per guide §8.3):

1. **Generation fence (submission intake)**
   - `SubmissionWritePort.submit` writes `submissions` row with a
     `generation_id` (UUID) before any Redis enqueue.
   - Fence: CAS on `(submission_id, generation_id)` prevents duplicate
     judge worker processing.
   - Implementation: MyBatis-Plus `OptimisticLockerInterceptor` or manual
     version column.

2. **Judge outbox (submit → judge worker)**
   - `judge_outbox` table: `id`, `submission_id`, `generation_id`,
     `status`, `created_at`, `claimed_at`, `delivered_at`.
   - Written in the same DB transaction as `submissions` row.
   - Dispatcher (App-owned scheduled job):
     - Claims pending rows (`WHERE status='PENDING' AND claimed_at IS NULL
       LIMIT N FOR UPDATE SKIP LOCKED`).
     - Enqueues to `JudgeQueue` (Redis Streams) with `generation_id`.
     - Updates `judge_outbox.status='CLAIMED', claimed_at=NOW()`.
   - Reaper: retries stale `CLAIMED` rows after lease expiry.

3. **Result outbox (verdict → downstream)**
   - NEW `result_outbox` table: `id`, `submission_id`, `verdict`,
     `score`, `time_ms`, `event_type`, `payload`, `status`,
     `created_at`, `delivered_at`.
   - Judge worker writes result outbox in the same DB transaction that
     updates `submissions` with verdict.
   - Downstream consumers (Contest, Notification, Achievement) read from
     `result_outbox` instead of direct `submissions` reads.
   - Eliminates "verdict committed but JVM crashed before fan-out"
     data loss (guide §8.3).

4. **Feature-flag driven dual-write window**
   - Feature flag `judge.use_outbox=true` enables outbox path; false
     keeps legacy `RQueue` path.
   - Dual-write period: both `RQueue` and `JudgeQueue` receive messages;
     compare delivery rates.
   - Exit criteria (legacy `RQueue` deprecation):
     - 99.9% of submissions flow through outbox path for 7 days.
     - No dispatcher/reaper errors for 3 days.
     - Monitoring shows `RQueue` dequeue rate < 0.1%.
   - Once exit criteria met: remove `RQueue` write path, switch flag
     permanently.

Alternatives:
- Keep `RQueue` indefinitely — rejected (technical debt; no generation
  fence; duplicate state).
- Skip result outbox, rely on direct `submissions` reads — rejected (JVM
  crash loses verdict fan-out, violates §8.3).
- Use RabbitMQ instead of Redis Streams — rejected (adds new infrastructure;
  guide §8.3 prefers "不立即换 MQ").

Consequences:
- Phase 0 design only; implementation starts Phase 2 (P2-JUDGE-001).
- Adds two new tables (`result_outbox`; `judge_outbox` already exists).
- Requires dispatcher/reaper scheduled jobs (App-owned).
- Judge worker gains a new write path (`result_outbox`).

Affected Tasks:
- P0-JUDGE-001 (this design).
- P2-JUDGE-001 (implementation of generation fence + judge outbox).
- P2-JUDGE-002 (result outbox + downstream consumers).
- P7-JUDGE-001 (legacy `RQueue` removal).