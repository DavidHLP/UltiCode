# P3-LEASE-001 — Fenced singleton leases

## Protocol

`admin.fenced_job_leases` is the shared control table for work that must have
one live runner across replicas. The table is created by the shared Flyway
migration so the ordered owner manifest can use it before the owner-specific
chains. `CURRENT_TIMESTAMP(3)` is the authority; host/JVM clocks are not used
to decide whether a lease is live.

Each row contains:

- `lease_name`: stable allowlisted job name;
- `fence_token`: monotonically increasing generation, incremented only after
  expiry or release;
- `owner_token`: unique runner identity;
- `leased_until`: database-clock expiry;
- `updated_at`: last acquisition/renewal timestamp.

Acquisition is an atomic insert/upsert. A live row stays with its current
owner; an expired row gets a new owner and a higher fence token. Renewal and
release require both owner and fence token. A completion write must also check
the same owner/token and `leased_until > CURRENT_TIMESTAMP(3)`.

## Callers

| Caller | Lease | Stale-work protection |
| --- | --- | --- |
| Admin reconciliation | `admin:reconciliation` | `reconciliation_runs` finish is a lease/token CAS |
| Admin scheduled backup enqueue | `admin:scheduled-backup` | only one replica can enqueue the scheduled run |
| Ordered owner migration | `admin:owner-migration` | shared Flyway bootstrap is serialized by Flyway; owner and post-owner phases renew/assert the lease |
| External backup/restore/prune runbook | `admin:owner-backup` | host `flock` is a local shortcut; DB lease protects replicas and archive publication checks the lease |

The backup runbook excludes `admin.fenced_job_leases` from business-table
archive/checksum reconciliation. It is ephemeral control state; restoring an
old owner token would resurrect a dead runner. The canonical migration
recreates the empty table on a restored target.

Submission outbox dispatchers and the judge lease reaper are not converted to
this global lease. They already use owner-local row claims, generation, and
attempt-id CAS; those per-item fences are the correct multi-runner behavior.

## Failure handling

- Duplicate runners: the second acquisition returns busy without starting work.
- Pause, partition, or crash: renewals stop; expiry permits a replacement with
  a higher token.
- Clock skew: the common `FencedLease` model applies a safety window and fails
  closed before the expiry boundary or after a backwards jump.
- Lost lease: the old runner may finish computing, but its completion CAS
  updates zero rows and cannot publish stale state.

The default lease TTL is 10 minutes for Admin Java jobs and 30 minutes for
migration/backup runbooks. `OWNER_FENCED_LEASE_TTL_MS` accepts 1000 ms through
24 hours. Production runbooks require the privileged migration/backup
identity to have the narrowly scoped Admin lease-table DML grant; no runtime
owner account is used for migration or backup.

## Validation

```bash
bash scripts/test/fenced-lease-contract.sh
bash scripts/test/owner-migration-manifest-contract.sh
bash scripts/test/owner-backup-restore-contract.sh
```

The first contract runs deterministic common-clock and Admin singleton tests,
then the real MySQL Testcontainers two-runner/expiry tests when Docker is
available. Production lease authority, backup storage, and migration execution
remain external operations.
