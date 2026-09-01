# P1-INFRA-005 MeiliSearch Degradation & Rebuild Contract

> status: FROZEN
> baseline: [`P0-BASELINE-004-infra-graph.md`](P0-BASELINE-004-infra-graph.md)
> scope: Search worker, App indexed reads, owner event boundaries, and disposable recovery evidence
> evidence level: Repository Implemented + Disposable Validatable. No production failover, recovery-time, or multi-host availability claim.

## 1. Boundary and source of truth

MeiliSearch is a **derived search index**, not an Owner backup and not a second business-data authority. The current path is:

```text
Owner local write
  -> owner integration outbox
  -> Redis stream:integration
  -> search-worker consumer group
  -> version ledger search:doc-version:<index>
  -> MeiliSearch (the only index writer)
```

The four derived indexes are `problems`, `users`, `posts`, and `solutions`. App is the owner publisher for problems, posts, and solutions. Auth owns the `users` index; App profile changes may publish a complete user document with the Auth owner tag, but the index ownership does not move to App. The Search worker never reads an App/Auth business table to fill in an event or repair a missing field.

Physical MeiliSearch separation, a new broker, and production HA/failover are outside this contract. Redis remains the existing shared logical seam described by P0-BASELINE-004.

## 2. Read contract

`DefaultSearchReadProjection` is the App read seam. The response always carries `SearchReadSemantics`, so clients do not infer consistency from deployment flags.

| Read mode | Entry condition | Source and failure behavior | Freshness | Ordering | Total |
|---|---|---|---|---|---|
| `DATABASE` | Default (`app.search.read.mode=database`) or explicit DB mode | Owner `SearchSource` adapters; a configured Meili client is ignored | `REALTIME` | `SOURCE_ID_ASC` | `EXACT` |
| `INDEXED` | Explicit indexed mode **and** `worker-enabled=true` | MeiliSearch first. A missing client, unavailable dependency, guard rejection, Meili error, runtime search error, or exact-total cap failure invokes the whole-request DB fallback only when `fallback-to-database=true`; otherwise the request fails closed with `IllegalStateException` | `EVENTUAL` | `MEILI_RELEVANCE_THEN_INDEX_ORDER` | `EXACT_UNDER_MAX_TOTAL_HITS` |
| Indexed request after fallback | Same indexed request, fallback enabled | One failure does not produce a mixed page: the complete request runs through `searchWithDatabase` | `REALTIME` | `SOURCE_ID_ASC` | `EXACT` |

The exact response values are therefore:

```text
DATABASE:               DATABASE / DATABASE / REALTIME / SOURCE_ID_ASC / EXACT / fallbackApplied=false
INDEXED:                INDEXED  / MEILISEARCH / EVENTUAL / MEILI_RELEVANCE_THEN_INDEX_ORDER /
                       EXACT_UNDER_MAX_TOTAL_HITS / fallbackApplied=false
INDEXED -> DB fallback: INDEXED  / DATABASE / REALTIME / SOURCE_ID_ASC / EXACT / fallbackApplied=true
```

### 2.1 Indexed totals and page mapping

- For a selected index, the projection asks MeiliSearch for an exhaustive total (`hitsPerPage=0`) before fetching the requested page.
- The result must be paginated and must remain strictly below that index's `pagination.maxTotalHits`. A total equal to or above the cap, or a non-exhaustive response, is an indexed-read failure and follows the fallback/fail-closed rule above.
- For an all-index query, totals are read for every available index, then the global offset is mapped in `SearchIndexType.values()` order: `PROBLEMS`, `USERS`, `POSTS`, `SOLUTIONS`. Hits are fetched only from indexes contributing to the requested page. Within each index MeiliSearch owns relevance ordering.
- `_aggregateVersion` and unknown forward-compatible hit keys are internal/index fields. The read projection does not expose them in `SearchResultItem.metadata`.

### 2.2 Database fallback order and owner predicates

For a selected index, the projection obtains the exact Owner count first and fetches only when the offset is in range. For an all-index query, it sums each source count and maps one global offset across the same fixed index order. Each source returns rows in ascending natural ID order; the projection truncates to the requested limit.

| Index | Owner read seam | Rows included | Stable order | Database document/read fields |
|---|---|---|---|---|
| `problems` | `ProblemSearchReadPort` | `is_published=true AND is_deleted=false`; title or slug match | `id ASC` | id, title, slug, difficulty |
| `users` | `UserSearchReadPort` -> `OwnerUserSearchReadAdapter` | non-deleted Auth accounts plus owner-composed App profile facts | Auth account `id ASC`; username/profile matches are merged and de-duplicated by account ID | account ID, username, optional name/avatar |
| `posts` | `ForumPostReadPort` | `is_deleted=false`; title or excerpt match | `id ASC` | id, title, excerpt, permalink |
| `solutions` | `SolutionReadPort` | `is_published=true AND is_deleted=false`; title or summary match | `id ASC` | id, title, summary, problem ID |

The database result is current at the Owner reads used for that request, but count and page reads are not a cross-Owner snapshot. It must be labeled `REALTIME`, not treated as a transactionally frozen global result.

## 3. Owner event and document invariants

The wire contract is schema version `1`, event type `SearchDocumentChanged`, and the existing integration envelope fields: `eventId`, `owner`, `eventType`, `schemaVersion`, `aggregateId`, `aggregateVersion`, `causationId`, `traceId`, and `payload`. Supported publishers are exactly `App` and `Auth`; supported operations are `UPSERT` and `DELETE` on the four allowlisted indexes.

Every UPSERT is a complete, index-safe JSON object. Current builders emit:

- problems: `id`, `title`, `slug`, `difficulty`;
- users: `id`, `username`, optional `name`, optional `avatar`;
- posts: `id`, `title`, `excerpt`, `permalink`;
- solutions: `id`, `title`, `summary`, `problemId`.

Search documents must not contain credentials or execution data, including `code`, `sourceCode`, `testCases`, `hiddenTestCases`, `accessToken`, `refreshToken`, `cookie`, `password`, or `token`; nested keys are checked recursively. A DELETE carries no document and is represented by an index delete plus a version-ledger tombstone.

Owner publishers append the complete event to their local outbox in the Owner write path. App uses `IntegrationEventPublisher`/`integration_outbox`; Auth uses its Auth-owned search outbox. The App-run backfill runner also uses the App integration outbox while retaining the Auth owner tag for `users`; physical outbox placement does not change index ownership. Dispatchers append to `stream:integration` and mark the outbox row delivered only after `XADD` succeeds. Outbox and stream failure must not be replaced with a direct App/Auth Meili write.

## 4. Search worker apply, replay, and DLQ contract

`SearchDocumentIndexWorker` is the sole MeiliSearch writer. Its scheduled cycle is ordered as:

1. ensure group `search-worker` exists from `0-0`;
2. refresh best-effort SLO gauges;
3. process pending entries (PEL reclaim/dead-letter) before new entries;
4. read new entries from the group's last-consumed offset.

The default stream is `stream:integration`, batch size is `50`, and the reclaim minimum idle time is `CLAIM_MIN_IDLE=30s`. A consumer uses an instance-unique name by default, or the explicit `SEARCH_WORKER_CONSUMER_NAME` override.

### 4.1 ACK and failure ordering

- Owner publishers validate safe-document keys recursively before writing the outbox. The worker validates the envelope, owner/index/operation, aggregate ID, schema version, and non-negative aggregate version before business effect; its accepted input is the publisher-produced contract and it must not be treated as a second document sanitizer.
- Unsupported owners, owner/index mismatches, unsupported indexes, future schema versions, malformed payloads, and invalid versions remain unacknowledged in the PEL. They are not silently converted to successful index writes.
- For a valid operation, the worker obtains a per-index/document Redis lease, reads the ledger, performs the MeiliSearch write task, and only then writes the ledger and ACKs the stream record. A Meili task being accepted is enough for this boundary; indexed reads remain eventual while Meili applies the task.
- A stale operation is a successful no-op: it performs no Meili write, is ACKed, and cannot block newer state.
- An ACK failure leaves the event replayable. Replayed writes are safe because document IDs are upserted and ledger checks prevent an older event from overwriting newer state.
- MeiliSearch failures leave the event in the PEL and do not advance the ledger. Lock contention also leaves it pending without inflating delivery count; the worker defers reclaim/dead-letter handling until the short lock-wait deadline expires. A lost lease after a successful Meili call fails closed for the ledger write, so a slow worker cannot clobber a newer owner's ledger entry.

### 4.2 Version ledger

The ledger is one Redis hash per index: `search:doc-version:<index>`, keyed by document ID. Event versions are non-negative epoch milliseconds (`aggregateVersion`); legacy version `0` is valid.

| Existing ledger value | Incoming operation | Exact action |
|---|---|---|
| absent or unparsable | UPSERT/DELETE | process normally (self-heal) |
| positive `V` | UPSERT/DELETE with `incoming < V` | skip as stale, then ACK |
| positive `V` | any operation with `incoming == V` | process again (idempotent rewrite/delete) |
| positive `V` | any operation with `incoming > V` | process and replace ledger |
| typed tombstone `D:T` | UPSERT/DELETE with `incoming <= T` | skip and ACK; no resurrection, including `T=0` |
| typed tombstone `D:T` | operation with `incoming > T` | process; a newer UPSERT can recreate the document |
| legacy negative tombstone `-T` | same ordering as `D:T` | read for compatibility; new DELETEs write `D:T` |

An UPSERT embeds its accepted `aggregateVersion` as `_aggregateVersion` in MeiliSearch. A DELETE writes `D:<incomingVersion>` only after the Meili delete task is accepted. The ledger is a coordination/replay watermark, not a business-data backup.

### 4.3 PEL and DLQ

Pending entries are inspected with `XPENDING`. Entries waiting on a document lease are skipped during reclaim. For other entries, the exact dead-letter boundary is `deliveryCount > maxAttempts` (default `maxAttempts=5`, so a pending row whose current count is `6` is first eligible), not `>=`. Entries below that boundary are claimed with `XCLAIM` after `30s` idle and retried.

The DLQ is `search:stream:dlq`. Dead-letter transfer is one Redis Lua operation:

1. `SET search:stream:dlq:seen:<source-stream-id> 1 NX EX 86400`;
2. `XADD` the original envelope fields to the DLQ;
3. `XACK` the original `stream:integration` entry for group `search-worker`.

If the DLQ append fails, the marker is removed and the source remains pending. The marker prevents duplicate DLQ records for the same source PEL ID; it does not make an index write non-idempotent. The DLQ retains the original `eventId`, owner, type, aggregate ID/version, schema version, causation/trace IDs, and payload for diagnosis/replay.

## 5. Rebuild/backfill contract

The existing `SearchBackfillRunner` is the rebuild mechanism. It is inert unless both `app.search.backfill.enabled=true` and `meilisearch.enabled=true`; a blank index selection means all four indexes, otherwise the value is a comma-separated subset of `problems,users,posts,solutions`. The current launcher/configuration seam is `APP_SEARCH_BACKFILL_ENABLED` with the indexed `dev-full`/production search flags; no new rebuild service or platform is introduced.

For each selected index independently:

1. Set watermark `W = now` (epoch milliseconds).
2. Enumerate the full Owner snapshot page by page until an empty page, using each Owner's stable natural-key ascending order. The existing read-port contract is stable only when rows are not written between page calls; this is a version-fenced convergence protocol, not a cross-table snapshot transaction.
3. Read existing MeiliSearch documents before publishing for that index, requesting only `id` and `_aggregateVersion`. If that index cannot be read, fail that index before publishing its events. There is no global transaction across multiple selected indexes; an earlier completed index may already have queued events.
4. Publish one UPSERT event per snapshot row with the row's last-change version and complete builder output.
5. For every existing index document absent from the snapshot, publish DELETE only when its existing version is strictly `< W`. Existing documents with version `>= W` are retained for their live events, preventing a concurrent create/update from being deleted by the diff. A missing existing `_aggregateVersion` is treated as version `0`; a malformed existing version fails the index preflight.
6. Publish all changes through the Owner integration outbox. The runner never calls a MeiliSearch write API; the Search worker remains the only writer.

The DELETE event in step 5 carries the existing document version (or `0` when absent), not `W`. The worker then applies its normal `D:T` tombstone rules. Each backfill event is independently persisted; a run that stops after some events have been queued is resumed by rerunning the same backfill.

### 5.1 Owner snapshot/version rules

| Index | Backfill source and predicate | Version supplied to the event |
|---|---|---|
| `problems` | App `ProblemSearchBackfillReadPort`: published and non-deleted problems, `id ASC` | problem `updated_at` epoch milliseconds; null maps to `0` |
| `users` | `UserSearchBackfillReadPort`: Auth account pages ordered by account ID, composed with App profile facts; no App query of Auth-owned tables | owner-composed `freshAt` (maximum available Auth/profile freshness); null timestamps map to `0` |
| `posts` | App `ForumPostSearchBackfillReadPort`: non-deleted posts, `id ASC` | post `updated_at` epoch milliseconds; null maps to `0` |
| `solutions` | App `SolutionSearchBackfillReadPort`: published and non-deleted solutions, `id ASC` | solution `updated_at` epoch milliseconds; null maps to `0` |

Backfill and live publishers use the same `SearchDocumentBuilders`, so a snapshot document has the same safe shape as a live document. A live event already newer than a snapshot is protected by the worker ledger; a live document created/updated during the run with version `>= W` is protected from diff deletion and converges through its own event.

### 5.2 Idempotent rerun and ledger reset

Rerunning a converged backfill may enqueue equal-version UPSERTs again. The worker rewrites the same document ID and preserves the same ledger value; no duplicate business document is created. Rerunning the diff does not enqueue a DELETE for a snapshot-present ID and does not delete an existing document at or beyond `W`.

A normal MeiliSearch outage does **not** justify deleting the stream, PEL, or version ledger. If the derived index was actually lost or restored empty, the operator must use a short authorized stop-write window before resetting the affected `search:doc-version:<index>` hash and running backfill. This prevents a stale ledger from suppressing the rebuild. Owner data and outbox rows remain authoritative throughout.

## 6. Failure and recovery evidence path

### 6.1 Normal outage/degradation

1. Confirm PM2/process state and logs, then inspect `XLEN stream:integration`, `XINFO GROUPS stream:integration`, `XPENDING stream:integration search-worker`, and `XLEN search:stream:dlq` using [`services/docs/WORKER_SLO_RUNBOOK.md`](../../../services/docs/WORKER_SLO_RUNBOOK.md) §2–§3. Do not delete the stream, PEL, or ledger as a first response.
2. If MeiliSearch is unavailable and DB fallback is enabled, `/search` returns the DB result with `INDEXED / DATABASE / REALTIME / SOURCE_ID_ASC / EXACT / fallbackApplied=true`. If fallback is disabled, the indexed request fails closed; it must not return an unlabeled partial index result.
3. Meili write failures leave search events in the PEL and leave their ledger entry unchanged. The worker readiness marker is refreshed only when both Redis `PING` and `meilisearch.health()` answer; a configured marker becomes stale and the Compose worker health gate fails when a hard dependency remains down. Indexing task failure alone does not fake readiness.
4. The SLO gauges distinguish unknown observation (`-1`) from an empty queue (`0`). Correlate queue lag, PEL depth/oldest age, DLQ size, `search_worker_consume_failures_total`, and `search_worker_last_success_timestamp` rather than paging on `-1` alone.

### 6.2 Dependency recovery

1. Restore MeiliSearch/Redis without clearing existing stream history, PEL, or version hashes.
2. Let a live worker reclaim pending events after the `30s` idle threshold. If all replicas died, restart one Search worker; its normal `drainPending()` path reclaims orphaned entries. Manual `XCLAIM` is an exception, not a second recovery implementation.
3. Verify the PEL drains, oldest pending age decreases, queue lag stops growing, `last_success` advances, and eventual indexed reads converge. Equal/replayed events must remain one document per ID and stale events must not overwrite the newer ledger state.
4. Use [`scripts/dev/drill-worker-failure.sh`](../../../scripts/dev/drill-worker-failure.sh) with `--app ulticode-search` for the existing dry-run/stop/restart observation flow. Its final evidence must be collected after the worker's reclaim idle threshold; the script's short post-restart observation is not by itself proof that a newly pending entry was reclaimable.

### 6.3 Derived-index rebuild after loss

Only in an authorized disposable target or an explicitly approved maintenance window:

1. Preserve the source commit, Owner backup/checksum metadata, selected indexes, and Redis/Meili observations.
2. Pause relevant Owner search mutations long enough to reset the affected ledger without racing live writes; do not flush `stream:integration`.
3. Reset only the affected `search:doc-version:<index>` hash, ensure MeiliSearch is reachable, then enable the existing App backfill runner (`APP_SEARCH_BACKFILL_ENABLED=true` with the existing indexed launcher/configuration).
4. Observe Owner outbox delivery, `stream:integration`, Search PEL/DLQ, version-ledger monotonicity, and Meili document counts. A backfill run must fail closed on a Meili preflight error and must be rerunnable after the dependency is fixed.
5. Resume writes only after the selected index's outbox/PEL drain and count/version checks are recorded. The resulting index is still derived and must not replace an Owner backup.

For a broader restore drill, [`docs/operations/backup-and-recovery.md`](../../operations/backup-and-recovery.md) requires restoring Owner data to a disposable target, clearing the affected version ledger, and rebuilding from Owner data; no production restore authority is claimed here.

### 6.4 DLQ replay/discard

After fixing the publisher/schema or Meili mapping root cause, inspect `XRANGE search:stream:dlq - + COUNT 10` and replay by re-adding the original envelope to `stream:integration`. Delete the DLQ entry only after the new stream append succeeds; leave the `search:stream:dlq:seen:<source-id>` marker until replay succeeds (the marker expires after 24 hours). Replaying an older event while live writes are active warrants the short stop-write coordination described in `WORKER_SLO_RUNBOOK.md` §5 because the marker prevents duplicate DLQ transfer, not old-version index effects. A true poison event should be discarded only after the publisher is fixed; replaying the same unsupported event will reach the same bounded DLQ boundary.

## 7. Disposable evidence matrix

The repository has focused proof surfaces for this contract. They are validation paths, not production evidence:

| Contract | Focused evidence |
|---|---|
| Read mode, fallback, exact totals/cap, global DB order, response metadata | `services/app/app-web/src/test/java/com/ulticode/modules/search/projection/DefaultSearchReadProjectionTest.java` |
| Worker owner/index validation, PEL retention, lock deferral, version ledger, tombstones, ACK ordering, atomic DLQ invocation | `services/search/src/test/java/com/ulticode/search/SearchDocumentIndexWorkerTest.java` |
| Real Redis Stream + Meili client transport, ACK/ledger/delete/stale replay and envelope-preserving DLQ | `services/search/src/test/java/com/ulticode/search/SearchWorkerEndToEndIT.java` |
| Real Redis + Meili event-to-query, DB fallback, duplicate replay, delete, and stale resurrection block | `services/app/app-web/src/test/java/com/ulticode/modules/search/projection/SearchEventToQueryE2EIT.java` |
| Watermark diff, `W` deletion fence, Meili preflight failure, index selection, and rerun convergence | `services/app/app-web/src/test/java/com/ulticode/modules/search/backfill/SearchBackfillRunnerTest.java` |
| Owner predicates, stable natural-key paging, document shapes, and row timestamp versions | `services/app/app-web/src/test/java/com/ulticode/modules/search/backfill/SearchBackfillReadPortIT.java` |
| Auth/App user composition, stable account order, and `freshAt` propagation | `services/app/app-web/src/test/java/com/ulticode/modules/search/port/OwnerUserSearchReadAdapterTest.java` |
| No App/Auth production Meili write API outside `backend-search` | `services/app/app-web/src/test/java/com/ulticode/modules/search/MeiliWritePathScanTest.java` |
| Operator inspection, PEL/DLQ replay, stop-write boundaries, and recovery steps | `services/docs/WORKER_SLO_RUNBOOK.md` §2–§6; `scripts/dev/drill-worker-failure.sh` |

The disposable limit is strict: do not infer production HA, zero downtime, exact RPO/RTO, cross-index atomicity, or external-host failover from these tests or runbooks. No new infrastructure platform is part of P1-INFRA-005.

## References

- [`P0-BASELINE-004-infra-graph.md`](P0-BASELINE-004-infra-graph.md)
- `services/search/src/main/java/com/ulticode/search/SearchDocumentIndexWorker.java`
- `services/app/app-web/src/main/java/com/ulticode/modules/search/projection/DefaultSearchReadProjection.java`
- `services/app/app-web/src/main/java/com/ulticode/modules/search/backfill/SearchBackfillRunner.java`
- `services/platform/common/src/main/java/com/ulticode/common/event/SearchDocumentChangedEventContract.java`
- `services/app/app-web/src/main/resources/application.yml` and `services/search/src/main/resources/application.yml`
- [`services/docs/WORKER_SLO_RUNBOOK.md`](../../../services/docs/WORKER_SLO_RUNBOOK.md)
- [`docs/operations/backup-and-recovery.md`](../../operations/backup-and-recovery.md)
