# P5-GATE-003 Admin RPC Budget Gate

> status: PASS (repository/disposable run)
> owner: ADMIN
> manifest: [`P3-ADMIN-001-admin-budget-manifest.md`](P3-ADMIN-001-admin-budget-manifest.md)
> entrypoint: [`scripts/test/gate-admin-rpc-budget.sh`](../../../scripts/test/gate-admin-rpc-budget.sh)
> evidence_level: repository source + focused Admin tests; no production evidence

P5-GATE-003 keeps the Admin budget manifest, fixed metric vocabulary, and the
instrumented Admin call shape in one fail-closed check. It is a repository and
disposable validation boundary, not a latency objective.

## Scope

The gate covers:

- all 84 fixed use-case IDs in P3-ADMIN-001's interactive, write, batch, and
  scheduled/reconciliation tables;
- the required table fields and finite `L / R / wall_budget_ms` targets;
- the fixed `admin.use_case.*` metric names, manifest ID set, and the bounded
  `use_case` / `owner` / `degradation` / `freshness` label vocabulary;
- Admin metric hooks with literal manifest IDs and explicit freshness metadata;
- known `@DubboReference` field calls (and `@DubboReference` declarations) in
  loop bodies under the complete Admin source scope; ordinary local loops remain
  valid, and the documented bounded `OwnerReconciler` batch loop is explicitly
  allowlisted;
- the focused metric tests `AdminUseCaseMetricsTest`,
  `DefaultAdminAnalyticsPortAdapterMetricsTest`, and
  `DefaultAdminDashboardReadAdapterMetricsTest`.

The script includes executable negative fixtures for a looped owner RPC, a
looped `@DubboReference`, an unknown metric use-case, and missing freshness. A
positive local-loop fixture proves that the loop check is not a blanket loop
ban.

## Checks and outcomes

| Check | Evidence | Failure classification |
| --- | --- | --- |
| Manifest completeness | Parses P3-ADMIN-001's four budget tables; requires the fixed 84-ID set, unique rows, required fields, finite non-negative targets, recognized policy/freshness values, and the repository boundary | `FAIL` |
| Metric names and IDs | Verifies `admin.use_case.logical_calls`, `serial_rounds`, `duration`, `degradation`, and `freshness`; checks the metric `USE_CASES` set equals the manifest | `FAIL` |
| Cardinality boundary | Rejects dynamic metric label names and any label outside `use_case`, `owner`, `degradation`, and `freshness` | `FAIL` |
| Static RPC-loop guard | Rejects known injected owner references in loop bodies; accepts local-only loops; runs the negative fixtures | `FAIL` |
| Metric-hook guard | Requires a fixed manifest ID and explicit `AdminUseCaseMetrics.Freshness` value for new `observe` hooks; rejects unknown and missing metadata fixtures | `FAIL` |
| Focused Admin tests | Runs the three metric tests through the Admin Maven slice | `PASS` |
| Toolchain/dependencies | Missing `python3`, `mise`, Java 17, Maven wrapper prerequisites, or resolvable Maven dependencies | `BLOCKED_EXTERNAL` |

`BLOCKED_EXTERNAL` is never converted to `PASS`.

The verified run on 2026-09-02 reported:

```text
manifest: PASS (84 fixed IDs and required fields)
metrics: PASS (fixed names/IDs and bounded labels)
static fixtures: PASS (RPC-loop and metric negatives rejected; local loop accepted)
focused test sources: PASS (Admin metric contract tests present)
admin-rpc-budget: focused Admin tests PASS
admin-rpc-budget: PASS (repository/disposable checks; no production claim)
```

## Run

From the repository root:

```bash
bash -n scripts/test/gate-admin-rpc-budget.sh
scripts/test/gate-admin-rpc-budget.sh
```

The script resolves the repository root from its own path, so the same entrypoint
also works when invoked from another directory. It captures focused Maven output
in a temporary file and does not print dependency logs or credentials on failure.

## Non-production boundary

This gate does **not** claim production latency, p95/p99 behavior, throughput,
capacity, availability, freshness, queueing, clock-skew tolerance, or an SLO.
`wall_budget_ms` remains the retry-inclusive repository arithmetic defined by
P3-ADMIN-001. A passing disposable test run is not production traffic evidence;
missing external tooling or dependencies remains `BLOCKED_EXTERNAL`.
