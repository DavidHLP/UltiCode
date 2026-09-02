# P5-GATE-004 Final Integration Matrix

> status: defined (repository/disposable gate)
> entrypoint: [`scripts/test/gate-final-integration.sh`](../../../scripts/test/gate-final-integration.sh)
> evidence level: repository source and disposable local resources only

P5-GATE-004 is the deterministic final repository gate. It invokes the existing
contracts and wrappers in a fixed dependency order, prints each child stream
verbatim, and stops at the first non-PASS result. The aggregator does not
reimplement any child contract.

## Phase matrix

A phase is complete only when every command assigned to it has the phase's
PASS condition. `FAIL` and `BLOCKED_EXTERNAL` both stop the gate. The final
status is PASS only when P0 through P6 complete with no non-PASS result.

| Phase | Commands / gate | PASS | FAIL | BLOCKED_EXTERNAL |
| --- | --- | --- | --- | --- |
| P0 Baseline | `manifest-contract`, `docs-contract`, `api-contract` | All three repository contracts exit zero without the marker. | The first contract that exits non-zero without the marker is the failing gate. | A child stream contains `BLOCKED_EXTERNAL`; the marker is never treated as green. |
| P1 App runtime boundary | `app-judge-runtime` | The App-to-Judge dependency contract exits zero without the marker. | The contract exits non-zero without the marker. | The child stream contains `BLOCKED_EXTERNAL`. |
| P2 Infrastructure recovery | `infra-isolation` | The infrastructure recovery gate exits zero without the marker. | The gate exits non-zero without the marker. | The child stream contains `BLOCKED_EXTERNAL`, or `FINAL_GATE_SKIP_EXPENSIVE=1` blocks the disposable drill. |
| P3 Admin RPC budget | `admin-rpc-budget` | The Admin budget gate exits zero without the marker. | The gate exits non-zero without the marker. | The child stream contains `BLOCKED_EXTERNAL`, or the expensive-mode flag blocks this gate. |
| P4 Legacy schema contraction (P4-011) | `schema-contraction` | The owner schema contraction contract exits zero without the marker. | The contract exits non-zero without the marker. | The child stream contains `BLOCKED_EXTERNAL`, or the expensive-mode flag blocks the disposable MySQL rehearsal. |
| P5 Affected Maven full suite | `repository-full` → Maven full test slice for changed owners/workers | The affected Maven slice exits zero without the marker. | Maven exits non-zero without the marker. | The child stream contains `BLOCKED_EXTERNAL`, or the expensive-mode flag blocks the suite. |
| P6 Disposable integration suite | `schema-integration`, `audit-stream-integration` | Both disposable contracts exit zero without the marker. | The first contract that exits non-zero without the marker is the failing gate. | A child stream contains `BLOCKED_EXTERNAL`, or the expensive-mode flag blocks integration. |

For every child, output classification is applied after the complete combined
stdout/stderr stream is emitted:

1. Any `BLOCKED_EXTERNAL` marker means `BLOCKED_EXTERNAL`, regardless of the
   child exit code.
2. With no marker, exit zero means `PASS`.
3. With no marker, non-zero means `FAIL`.

A child that emits `BLOCKED_EXTERNAL` can therefore never be rewritten as PASS.

## Command-to-gate mapping and order

The executable gate runs the following sequence. The order is part of the
contract and is independent of the caller's current working directory:

| Order | Phase | Gate label | Invoked command | Expensive-mode handling |
| ---: | --- | --- | --- | --- |
| 1 | P0 | `manifest-contract` | `bash scripts/dev/devstack-manifest-test.sh` | Always run |
| 2 | P0 | `docs-contract` | `bash scripts/dev/docs-contract-test.sh` | Always run |
| 3 | P0 | `api-contract` | `bash scripts/test/api-contract-boundary-contract.sh` | Always run |
| 4 | P1 | `app-judge-runtime` | `bash scripts/test/app-judge-runtime-dependency-contract.sh` | Always run |
| 5 | P2 | `infra-isolation` | `bash scripts/test/gate-infra-isolation.sh` | Blocked when `FINAL_GATE_SKIP_EXPENSIVE=1` |
| 6 | P3 | `admin-rpc-budget` | `bash scripts/test/gate-admin-rpc-budget.sh` | Blocked when `FINAL_GATE_SKIP_EXPENSIVE=1` |
| 7 | P4 | `schema-contraction` | `bash scripts/test/owner-schema-contraction-contract.sh` | Blocked when `FINAL_GATE_SKIP_EXPENSIVE=1` |
| 8 | P5 | `repository-full` | `(cd services && mise exec java@zulu-17.68.203.0 -- bash ./mvnw -pl auth,admin,app/app-web,submission,judge,notification,search -am test -B)` | Blocked when `FINAL_GATE_SKIP_EXPENSIVE=1` |
| 9 | P6 | `schema-integration` | `bash scripts/test/owner-schema-contraction-contract.sh` | Blocked when `FINAL_GATE_SKIP_EXPENSIVE=1` |
| 10 | P6 | `audit-stream-integration` | `bash scripts/test/admin-audit-stream-migration-contract.sh` | Blocked when `FINAL_GATE_SKIP_EXPENSIVE=1` |

The default is complete (`FINAL_GATE_SKIP_EXPENSIVE` unset or `0`). The
operator-controlled `FINAL_GATE_SKIP_EXPENSIVE=1` mode does not claim a pass:
it emits a `BLOCKED_EXTERNAL` result for the first expensive gate reached and
stops there. Any other value is a configuration failure.

## Stop conditions and stable result

- The first non-PASS child is the only reported failing gate. The script does
  not continue to later phases after `FAIL` or `BLOCKED_EXTERNAL`.
- Child output is captured as one combined stream and printed before its result
  line; no child diagnostics are discarded by the aggregator.
- A complete successful run ends with the exact stable line:

  ```text
  FINAL_GATE: PASS
  ```

- A blocked run ends with `FINAL_GATE: BLOCKED_EXTERNAL gate=Pn/<gate-label>`.
- A failed run ends with `FINAL_GATE: FAIL gate=Pn/<gate-label>`.
- The process exits zero only for the stable final PASS. FAIL and
  BLOCKED_EXTERNAL exit non-zero.

## P4-011 and GATE-FINAL boundaries

P4-011 remains the separately invoked owner schema contraction contract. Its
parity, grant, proof-row, and forward-contraction assertions run against the
contract's disposable MySQL resources; the final integration gate does not
alter migration history or apply a production schema change.

GATE-FINAL is an orchestration boundary, not a replacement for any child gate.
It does not invoke production Compose, deployment, publishing, push, remote
credential rotation, or production traffic. It does not source or manufacture
secrets or environment values; each existing child owns its own prerequisite
and fail-closed behavior. Local Compose, Testcontainers, sandbox, and MySQL
resources started by an existing child remain disposable resources.

A PASS here means only that the configured repository and disposable checks
returned PASS in this run. It does not establish production availability,
capacity, failover, SLO, RPO/RTO, zero-downtime behavior, external telemetry,
real-user traffic, or a production database change.

## Retained targeted commands

The final gate composes the commands above without removing their standalone
entrypoints. Use these retained commands for focused diagnosis or an
operator-selected verification slice:

```bash
# Repository wrappers
./scripts/dev/test.sh quick
./scripts/dev/test.sh full
./scripts/dev/test.sh integration

# Architecture and documentation contracts
bash scripts/dev/architecture-contract-test.sh
bash scripts/dev/docs-contract-test.sh

# Phase-specific contracts and gates
bash scripts/test/app-judge-runtime-dependency-contract.sh
bash scripts/test/gate-infra-isolation.sh
bash scripts/test/gate-admin-rpc-budget.sh
bash scripts/test/owner-schema-contraction-contract.sh

# Complete P5-GATE-004 run
scripts/test/gate-final-integration.sh
```

These focused commands retain their own output and failure behavior. They are
not production deployment commands and must not be interpreted as production
proof.
