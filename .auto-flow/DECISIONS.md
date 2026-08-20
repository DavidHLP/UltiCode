
## ARCHFIX-DEC-005: UserDirectoryQueryPort is the bounded ARCHFIX-003 seam

- **Context**: `AdminAnalyticsPort` and `SubmissionAdminReadPort` already provide independent coarse owner seams. `OwnerUserSearchReadAdapter` still composes Auth account, Auth identity and App profile reads for Search, so merging all three domains would create a new god contract.
- **Decision**: Introduce only an App-owned `UserDirectoryQueryPort` for Search user-directory reads. Its contract is versioned, bounded and batch-oriented: text search, stable id-ascending enumeration, and batch lookup by account ids. The returned row contains safe display fields plus source freshness/version metadata. Auth owns account/identity data; App owns profile projection; the adapter hides composition behind this single seam.
- **Alternatives**: Merge Admin analytics and Submission admin reads into one universal Query interface (rejected: unrelated lifecycle and ownership); keep per-user synchronous enrichment (rejected: leaks timeout/retry/freshness composition); immediately build an event projection (deferred: larger migration than this atomic slice).
- **Consequences**: Search callers stop knowing the Auth/App fan-out. Existing Admin and Submission contracts remain unchanged. Old adapter remains rollback-only until ARCHFIX-005.
- **Affected Tasks**: ARCHFIX-003, ARCHFIX-004, ARCHFIX-005

## ARCHFIX-DEC-006: Unify Search pagination, totals, and fallback

Context: one provider has inconsistent MeiliSearch/database semantics. Decision: preserve SearchReadProjection and SearchResponseVO, enforce common offset and exact totals, and fall back for the whole request on MeiliSearch failure. Alternatives (keep inconsistency, remove fallback, second provider, partial results) rejected. Consequences: sources need count-capable reads; no writer/migration/schema changes. Affected: ARCHFIX-004-001..004, ARCHFIX-005, ARCHFIX-006.

## ARCHFIX-DEC-007: Atomic Search read-contract migration and bounded user merge

- Context: SearchSource already accepts an offset but the four source implementations ignore it because their app-api read seams expose only query + limit; exact totals are unavailable. SolutionReadPort also has an existing Admin Dubbo consumer, and user search composes Auth accounts with App profiles.
- Decision: Migrate the source rows/count contract, all four source implementations, app-api read seams, Solution provider/Admin adapter, and focused callers in one compile-safe slice. Preserve the existing two-argument Solution method as a compatibility forwarder. Extend UserDirectoryQueryPort with paged/count reads implemented as account_id-ascending, bounded Auth/App batches with duplicate filtering; do not join Owner tables or materialize an unbounded result set.
- Ordering: The DB fallback user source is deterministic account_id ASC; the public SearchResponseVO fields and envelope remain unchanged. Meili and DB aggregation use the same fixed SearchIndexType order.
- Configuration: App read configuration adopts the existing MEILI_HOST / MEILI_MASTER_KEY names used by Compose and the worker; local default remains disabled.
- Alternatives: A contract-only signature change (rejected: known compile break), page-size-as-total (rejected: fabricated totals), cross-Owner SQL/new projection table (rejected: violates current Owner/schema scope), and unbounded profile materialization (rejected: OOM/latency risk).
- Affected Tasks: ARCHFIX-004-001..004; no migration, writer or Search provider count changes.

## ARCHFIX-DEC-008: Preserve compatibility paths until external retirement gates

- Context: ARCHFIX-004 real Search evidence is complete, but ARCHFIX-005 covers shared local/remote/legacy/shadow retirement across the Owner topology. Current production Compose still selects the remote Submission route and documents rollback to a prior compatibility artifact.
- Decision: Keep compatibility providers, routing switches, shadow/rollback paths and legacy adapters intact until external owner/release/DBA authority, all-writer quiesce, route/grant cutover, observation window and rollback/reconciliation evidence exist. TEST-TARGET rehearsal and disposable E2E cannot authorize retirement.
- Historical consequence: ARCHFIX-005 was blocked and ARCHFIX-006 was pending before the 2026-08-20 development-only authority override; no deletion or route switch was fabricated at that time.

## ARCHFIX-DEC-009: Development-only environment authority supersedes production blocker

- **Context**: The user confirmed on 2026-08-20 that this project has no production environment, only the current development/TEST-TARGET, and authorized blocker remediation there.
- **Decision**: Treat the current development/TEST-TARGET as the sole acceptance target for ARCHFIX-005/006. Permit reversible route, writer-quiesce, observation, rollback rehearsal and compatibility retirement work after current source/caller evidence proves it safe.
- **Boundaries**: Never describe development evidence as production acceptance; do not push, publish, change external resources, edit applied migrations or perform destructive data operations. Preserve single-writer, grant, reconciliation and rollback invariants.
- **Consequence**: ARCHFIX-005 was reopened for development execution; the prior external production-authority blocker no longer blocked it. ARCHFIX-005 is now closed by DEC-010, with ARCHFIX-006 as the final development gate.

## ARCHFIX-DEC-010: Retire shadow runtime while preserving dev-lite rollback seams

- **Context**: The project supports a local `dev-lite` profile and a remote/owner `dev-full` profile; removing the local writer/read path would break the supported minimal development startup and eliminate a runnable rollback seam.
- **Decision**: Retire App shadow/outbox runtime in `dev-lite`, enable the owner/Streams path only in `dev-full`, and feed App and Judge the same profile flags. Keep source-level local adapters/dispatcher/reaper as explicit rollback seams; do not silently dual-write.
- **Evidence**: Profile assertions, routing/provider tests, disposable Submission cutover/rollback and owner grant isolation passed. This is development acceptance only.
- **Consequence**: ARCHFIX-005 closes as runtime retirement with rollback seams preserved; ARCHFIX-006 is the final development gate.
