# ADR-0007: Legacy compatibility lifecycle and retirement floor

- **Status**: Accepted
- **Date**: 2026-09-02
- **Compatibility owner**: `Architecture/Release Maintainer`
- **Decision scope**: repository policy and disposable verification; production execution remains external

## Context

The App still contains an explicitly selected `legacy-rollback` profile. It keeps local Submission reads and the App/Judge compatibility adapter reachable for rollback, while normal reads and writes use Submission Owner contracts. The compatibility path must not become a second normal runtime or an unbounded promise to unknown old consumers.

The repository has no production environment, registry, traffic plane, or deployment inventory. Repository source, release metadata, and disposable rehearsals therefore establish implementation and decision rules only; they cannot establish a production cutover, a drained consumer, or a production rollback.

## Evidence and derived release floor

| Evidence | Observed repository fact | Decision consequence |
| --- | --- | --- |
| Release tags (`git tag --list`, latest selected by `git describe --tags --abbrev=0`) | The latest repository tag is `v3.0`; older tags include `v2.0`, `v1.9`, `v1.5`, `v1.4`, `v1.3`, and `v1.2`. | `v3.0` is the repository release anchor, not proof of a deployed old consumer. |
| Contract baseline workflow | `.github/workflows/_contract.yml` selects the latest tag by default, but explicitly skips comparison when the baseline has no standalone `services/api/*` contracts. The `v3.0` tree is the older `backend-spring` layout and has no standalone API contract set. | Do not infer a contract version or N-1 consumer from `v3.0`. A tag without standalone contracts is not a valid old contract artifact. |
| Current contract baseline | `services/pom.xml` sets reactor `revision` to `2.0.0`; `services/docs/CONTRACT_COMPAT_GATE.md` defines the four provider-owned API modules and their consumer matrix. | The current contract major is `2.0.0`. Same-major changes require the japicmp gate; major retirement requires the explicit retirement contract. |
| Release matrix | `.github/services-matrix.json` is the source for independently released backend artifacts and their `service.version.*` properties. Current service properties are `1.0.0`. | Matrix versions identify repository release inputs only; they do not identify deployed versions. Rollback descriptors must bind the selected matrix entries to immutable image digests. |
| Consumer and legacy inventory | `P0-BASELINE-005` and `P4-LEGACY-005-runtime-closure` show the remaining `legacy-rollback` implementation is rollback-only. `CONTRACT_COMPAT_GATE.md` records no deployed N-1 consumer or registry for the retired Submission contracts; its 14-day ledger is virtual and explicitly not production evidence. | No supported old consumer is currently present in the repository inventory. Closure is therefore conditional on the deployment authority confirming the same absence, not an assertion that production has already cut over. |

### Floor and N-1 rule

The compatibility floor is **one immediately preceding, verified standalone contract release (N-1), and never older**. The floor is identified by an exact release tag, contract revision, consumer identity, and immutable artifact descriptor; a historical tag is not admitted merely because it exists.

For the current repository, the floor is **unpopulated/conditional**: the nearest tag `v3.0` cannot supply an old standalone API artifact, and no supported old consumer is recorded. `2.0.0` is the current contract revision, not evidence of a supported `1.x` consumer. If a deployment authority later registers an old consumer, `Architecture/Release Maintainer` must record that consumer and its exact verified N-1 baseline before retaining compatibility. If no old consumer is registered, the N-1 set is empty and the legacy path may proceed to conditional closure after the gates below.

## Decision

1. **GO — lifecycle policy**: keep `legacy-rollback` explicit and rollback-only while the ordered P4 closure work is incomplete. Normal App traffic MUST remain on the Owner route; the compatibility path MUST NOT be reintroduced as a default writer or read path.
2. **CONDITIONAL GO — closure**: close the current compatibility profile and delete its implementation only after the closure criteria are satisfied and an external deployment inventory, when one exists, confirms that no supported old consumer remains. With no supported old consumer currently recorded, this is a conditional repository decision, not a production cutover claim.
3. **NO-GO — production claim**: do not claim production traffic drain, consumer retirement, production schema cutover, or production rollback readiness from these repository facts, the virtual ledger, or disposable validation.

## Lifecycle controls

### Owner and expiry

`Architecture/Release Maintainer` owns the consumer inventory, release-floor record, expiry decision, descriptor retention, and the final legacy-removal handoff. The owner must re-evaluate this ADR at every release that changes a contract revision, service matrix entry, route/profile, schema/migration state, or rollback artifact.

Compatibility authorization expires at the earlier of:

- the first release boundary after the single allowed N-1 window has elapsed; or
- the point at which the conditional closure criteria pass and no supported old consumer remains.

There is no invented calendar expiry because this repository has no deployment clock or production release history. An old consumer discovered before expiry blocks deletion and requires a new exact floor and expiry record; it does not silently extend this ADR. A second preceding release is never covered by this decision.

### Quiescence

Before closing or deleting the seam, the release operator must quiesce the legacy route: stop new legacy writes and reads, drain the selected legacy consumers, and confirm that no legacy mode is accepting new work during the observation window. Unconsumed outbox, Redis PEL, and Inbox work must either be drained through the owning recovery path or be preserved and explicitly accounted for; it must not be flushed to make the check pass. A production quiescence result requires deployment-authority evidence and is not supplied by this ADR.

### Checksum and parity

The closure packet must bind, without secrets:

- source commit and release tag;
- the selected entries from `.github/services-matrix.json`, including immutable image digests;
- contract revision and provider/consumer inventory;
- migration/schema manifest checksum and relevant schema checksum;
- parity/checksum results for the migrated Submission facts, with missing, extra, conflict, and unexplained-difference counts; and
- the checksum of the descriptor itself.

A checksum mismatch, missing descriptor field, unexplained parity difference, or stale consumer identity is a fail-closed result. The repository's checksum tooling and disposable parity are implementation evidence only.

### Rollback descriptor requirement

No compatibility implementation may be deleted or a profile made unreachable unless an immutable, secret-free **previous full release descriptor** is retained. It must name the source commit/tag, service versions and image digests, contract revision, schema/migration checksum, route/profile settings, and approved inputs needed to restore the release floor. The descriptor must be independently checksum-verified and proven schema/contract-compatible before use.

Rollback uses that complete old artifact set, not a current binary that permanently carries the old implementation. It first quiesces new writers, preserves unconsumed durable work, restores the old route, and uses `skip_migrations=true`; it MUST NOT downgrade schema, reopen cross-Owner grants, or resurrect a deleted writer. This follows [`docs/operations/deployment.md`](../../operations/deployment.md) and [`docs/operations/database-migrations.md`](../../operations/database-migrations.md).

## Closure criteria

The compatibility seam may be marked closed only when all of the following are evidenced:

1. `P4-LEGACY-005` remains true: normal App code has no legacy runtime leakage, and any remaining `backend-judge-runtime` dependency is only the explicitly guarded compatibility closure.
2. Every current read/write path is Owner-routed; no local Submission mapper/projection is reachable in the normal profile.
3. The consumer inventory has no supported old consumer. If an external deployment exists, its authority has supplied an inventory and drain result; if none exists, the record stays explicitly conditional and production adoption remains external.
4. The final `P5-GATE-001`/legacy-removal gate passes its profile reachability, dependency, symbol, contract, checksum, grant, and rollback checks.
5. The `legacy-rollback` profile is not default-reachable, and the ordered P4 steps remove compatibility implementation, local persistence knowledge, and the Maven dependency without skipping the prescribed gates. Schema contraction remains a separate task and is not implied by this ADR.
6. Quiescence, parity/checksum, and the immutable previous-release descriptor are present, recoverable, and tied to the same release-floor record.

If any criterion is missing, retain the explicit rollback seam, mark the missing evidence as external or blocked, and re-evaluate the owner/floor/expiry rather than claiming closure.

## Re-evaluation triggers

Re-open this ADR before the next release or deletion step when any of these occurs:

- a new release tag or standalone contract baseline appears;
- a consumer, registry entry, route, profile, or artifact using the legacy contract is discovered;
- a contract major/revision, service matrix entry, schema, migration, or owner route changes;
- quiescence, checksum/parity, rollback descriptor, or dependency/profile gates fail; or
- a deployment authority requests production cutover, rollback, or support for a second-oldest release.

A new compatibility window requires a new ADR or an explicit superseding update; it cannot be created by extending a stale expiry field.

## Evidence boundary

This ADR is `Repository Implemented` policy with disposable-verifiable inputs. It deliberately excludes production evidence: no production registry, active old-consumer inventory, traffic drain, credentials, migration authority, schema cutover, or rollback execution is claimed. The authoritative supporting records are [`P0-BASELINE-005`](../evidence/P0-BASELINE-005-legacy-graph.md), [`P4-LEGACY-005`](../evidence/P4-LEGACY-005-runtime-closure.md), [`P5-GATE-001`](../evidence/P5-GATE-001-baseline-gate-report.md), [`CONTRACT_COMPAT_GATE.md`](../../../services/docs/CONTRACT_COMPAT_GATE.md), [`SERVICES_ISSUES.md`](../../../services/docs/SERVICES_ISSUES.md), and [`current-status.md`](../../project/current-status.md).
