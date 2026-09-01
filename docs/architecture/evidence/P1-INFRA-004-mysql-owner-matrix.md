# P1-INFRA-004 MySQL Owner Impact, Connection Budget & Recovery Matrix

> status: COMPLETE (repository and disposable evidence)
> scope: the five MySQL Data Owners; no source/config change
> dependency: `P0-BASELINE-004-infra-graph.md`
> boundary: one shared MySQL instance today; schema/account isolation is not instance-resource isolation

## 1. Evidence boundary

The repository has five Owner schemas: `auth`, `admin`, `app`, `notification`, and `submission`. The local environment contract names the runtime accounts `auth_rw`, `admin_rw`, `app_rw`, `notification_rw`, and `submission_rw`; the separate `migration_submission` identity is privileged migration-only input, not a runtime account (`.env.example:32-63`). Production Compose requires explicit Owner connection values rather than falling back to the shared `DB_*` compatibility identity (`docker-compose.prod.yml:115-126`, `:214-218`, `:308-312`, `:418-422`, `:567-571`).

Each Owner datasource points at its `*_DB_HOST`, `*_DB_PORT`, `*_DB_NAME`, and `*_DB_USER` values (`services/auth/src/main/resources/application.yml:27-31`, `services/admin/src/main/resources/application.yml:15-19`, `services/app/app-web/src/main/resources/application.yml:16-20`, `services/notification/src/main/resources/application.yml:14-18`, `services/submission/src/main/resources/application.yml:10-17`). P0 records one authoritative MySQL instance with schema/account isolation but a shared instance fault domain; primary loss or pool exhaustion has no application fallback (`P0-BASELINE-004-infra-graph.md:14-18`, `:28-36`).

**Pool budget means the JDBC/Hikari pool for one running Owner JVM.** Scheduler pools, Redis pools, MySQL `max_connections`, replica counts, and deployment-wide connection totals are different budgets. The repository does not provide enough evidence to calculate an aggregate MySQL connection ceiling.

## 2. Owner matrix

| Owner / schema | Runtime account contract | JDBC pool budget in current config | Noisy-neighbor path on the shared instance | Backup, checksum, restore and recovery position |
| --- | --- | --- | --- | --- |
| **Auth / `auth`** | Local/example: `auth_rw`; production must supply `AUTH_DB_HOST`, `AUTH_DB_PORT`, `AUTH_DB_NAME`, `AUTH_DB_USER`, and password explicitly. | **Missing evidence.** The datasource block has no `spring.datasource.hikari.maximum-pool-size`, `minimum-idle`, or Auth-specific pool variable (`services/auth/src/main/resources/application.yml:27-32`). Do not infer a numeric effective pool from this repository. | Credential, account-status, OAuth, refresh-session, and RBAC traffic can consume shared MySQL connection slots and CPU/IO. Because the instance is shared, contention can slow every other Owner; Auth has no documented database fallback on primary loss (`P0-BASELINE-004-infra-graph.md:16`, `:30-33`). | **Restore import #2**, after control schema `ulticode`: `auth.sql` is in the all-schema encrypted archive. Its dump SHA-256 is listed in `checksums.sha256`; table rows plus `CHECKSUM TABLE` values are in `table-checksums.tsv`; Flyway history is in `migration-metadata.tsv`. Run owner Flyway validation after import. Any live rollback still requires writer quiescence and a schema-compatible artifact; the drill target is disposable. |
| **Admin / `admin`** | Local/example: `admin_rw`; production must supply `ADMIN_DB_*` values explicitly. | **Missing evidence.** The datasource block does not declare JDBC/Hikari pool settings (`services/admin/src/main/resources/application.yml:15-20`). `audit-pool-size=2`, `reconciliation-pool-size=1`, and `backup-pool-size=1` are scheduler budgets, not MySQL connection-pool budgets (`services/admin/src/main/resources/application.yml:50-55`). | Audit/reconciliation reads and backup orchestration can add shared instance CPU/IO and connection pressure. The complete backup job belongs to the Admin operational domain but is executed by the external Ops runbook with a distinct privileged backup identity and dumps all six schemas, so the work is instance-wide rather than an Admin-schema-only resource guarantee. | **Restore import #3**. `admin.sql` is included and verified like the other Owner dumps. `admin.fenced_job_leases` is intentionally excluded from dump and table checksums because it is ephemeral control state; the canonical migration recreates the empty table (`docs/operations/backup-and-recovery.md:9-18`, `scripts/runbooks/owner-backup-restore.sh:139-148`, `:211-225`). Never treat the lease row as business data to restore. |
| **App / `app`** | Local/example: `app_rw`; production must supply `APP_DB_*` values explicitly. | **Missing evidence.** No JDBC/Hikari pool settings are declared in the App datasource block (`services/app/app-web/src/main/resources/application.yml:16-22`). The App scheduled executor size of 4 is not a JDBC budget (`services/app/app-web/src/main/resources/application.yml:32-39`). | Problem, contest, community, profile, and interaction queries/writes can consume shared connections and instance CPU/IO. Long-running or bursty App work therefore propagates to Auth, Admin, Notification, and Submission even though the App account is schema-scoped. | **Restore import #4**. `app.sql` receives archive/dump SHA-256, per-table row/checksum reconciliation, and Flyway-history validation. Search indexes are derived, not business backup: if Search is restored, clear `search:doc-version:{index}` and rebuild from Owner data (`docs/operations/backup-and-recovery.md:17-20`). |
| **Notification / `notification`** | Local/example: `notification_rw`; production must supply `NOTIFICATION_DB_*` values explicitly. | **Missing evidence.** The datasource block declares no JDBC/Hikari pool size or Notification-specific pool variable (`services/notification/src/main/resources/application.yml:14-20`). Its scheduling pool size of 4 is not a JDBC budget (`services/notification/src/main/resources/application.yml:41-48`). | Notification, preference, delivery-ledger, retry, and email work can consume shared instance connections and CPU/IO. A Notification burst or slow transaction can therefore increase latency or pool wait for every Owner; schema grants do not cap shared instance resources. | **Restore import #5**. `notification.sql` is covered by the same encrypted archive, dump digest, row/table checksums, and migration metadata. Recovery must preserve the owner’s ledger/inbox semantics; any live cutover/rollback must stop and drain writers before grant or route changes (`docs/operations/database-migrations.md:19-27`). |
| **Submission / `submission`** | Local/example: `submission_rw`; production requires `SUBMISSION_DB_USER` and password, with `SUBMISSION_DB_NAME` defaulting to `submission` in the production overlay. `migration_submission` remains separate for migration work (`.env.example:55-63`, `docker-compose.prod.yml:416-427`). | **Explicit per-process cap:** `maximum-pool-size=${SUBMISSION_DB_POOL_SIZE:8}`, `minimum-idle=2` (`services/submission/src/main/resources/application.yml:10-17`). This bounds one Submission JVM only; it is not an instance-wide cap. | Submission intake, verdict, generation/fence, and judge/result/created outbox work can occupy up to the configured per-process pool and shared MySQL resources. Other Owner pool totals are unknown, so the cross-owner connection budget and worst-case contention remain unquantified. | **Restore import #6**. `submission.sql` is imported last among Owners, then validated and reconciled by rows and `CHECKSUM TABLE`. On a live rollback, preserve unconsumed outbox/PEL/inbox state and quiesce/drain writers before route changes; do not use backup restore as a schema downgrade (`docs/operations/deployment.md:26-30`). |

### Pool-budget conclusion

Only Submission has a repository-declared JDBC budget: **8 maximum / 2 minimum idle per process**, with `SUBMISSION_DB_POOL_SIZE` as its maximum override. Auth, Admin, App, and Notification have **no explicit JDBC pool evidence** in their current datasource configurations. Therefore this matrix intentionally does not invent defaults or a total such as `5 × N`; the number of Owner JVM replicas and MySQL server connection budget are deployment inputs not present here. The shared-instance consequence is still explicit: one Owner’s CPU/IO/connection pressure can affect all five, and primary loss affects all five with no database fallback (`P0-BASELINE-004-infra-graph.md:28-36`).

## 3. Common backup and checksum contract

The complete artifact covers control schema `ulticode` plus all five Owner schemas (`scripts/runbooks/owner-backup-restore.sh:32-52`, `docs/operations/backup-and-recovery.md:3-7`). It is an OpenSSL-encrypted archive; the operator supplies the key, which must be a 64-hex-character (32-byte) value and is not stored in Git or logs (`scripts/runbooks/owner-backup-restore.sh:94-97`). Each schema dump is produced with `--single-transaction`, routines, triggers, and UTF-8 settings (`scripts/runbooks/owner-backup-restore.sh:139-157`).

The artifact records all of the following:

- a SHA-256 digest for each `dumps/<schema>.sql` file;
- the encrypted archive SHA-256, byte count, owner-schema list, control-schema name, and migration metadata in the secret-free manifest;
- per-table row counts and MySQL `CHECKSUM TABLE` values for every base table, excluding `admin.fenced_job_leases`;
- Flyway history snapshots for each schema and the shared post-owner history.

These are integrity and parity checks, not production SLOs. A fenced database lease named `admin:owner-backup` plus a local `flock` serializes backup, restore-drill, and prune (`docs/operations/backup-and-recovery.md:9-11`, `scripts/runbooks/owner-backup-restore.sh:160-180`). The lease is a runbook concurrency guard; it is not a claim that all application writers are quiesced. The backup loop still dumps schemas sequentially with per-dump `--single-transaction` (`scripts/runbooks/owner-backup-restore.sh:270-324`).

## 4. Restore-drill order and gates

**Every restore target in this evidence is disposable.** The documented `restore-drill` restores only to a one-time MySQL target, and the script creates a disposable `mysql:8.0` container named `ulticode-owner-restore-drill-*` and removes it on exit (`docs/operations/backup-and-recovery.md:5-7`, `scripts/runbooks/owner-backup-restore.sh:433-452`). This does not establish production restore authority, production RPO/RTO, or transparent failover.

The exact restore and validation sequence is:

1. Confirm a disposable/authorized target; save the source commit, schema checksum, manifest, and key reference.
2. Select the manifest, verify its format and encrypted archive SHA-256, decrypt it, reject unsafe archive entries, require all six dumps and metadata files, and verify every dump digest (`docs/operations/backup-and-recovery.md:13-17`, `scripts/runbooks/owner-backup-restore.sh:341-373`).
3. Import schemas in this order: **`ulticode` → `auth` → `admin` → `app` → `notification` → `submission`** (`scripts/runbooks/owner-backup-restore.sh:453-457`).
4. Run Flyway validation in this order: shared `ulticode` migrations, each Owner’s migration location in `auth → admin → app → notification → submission` order, then `post-owner` controls (`scripts/runbooks/owner-backup-restore.sh:459-463`).
5. Reconcile every recorded table’s rows and checksum, require all six schemas, and run query smoke (`scripts/runbooks/owner-backup-restore.sh:465-478`). The runbook’s restore report records measured drill fields, but those fields are evidence for that disposable run only.
6. Run the documented query, service-readiness, queue/Inbox, and critical-API smoke checks. Treat Search as rebuildable derived state, never as a substitute for Owner business data (`docs/operations/backup-and-recovery.md:17-20`). Any bad key, missing file, archive/checksum/schema mismatch, unsafe target, busy lease, or smoke failure must exit non-zero (`docs/operations/backup-and-recovery.md:21-23`).

## 5. Migration preflight, quiescence and rollback constraints

Before an Owner migration, the preflight requires explicit migration connection values; the migration database name must equal the Owner schema; the migration account must differ from that Owner’s runtime account; the effective MySQL account/database and schema must match; the schema must exist; and grants must be direct rather than role-inherited (`scripts/dev/migrate.sh:129-186`). Required owner-schema privileges are `CREATE`, `ALTER`, `SELECT`, `INSERT`, `UPDATE`, `DELETE`, `INDEX`, and `REFERENCES`; App additionally requires `DROP`, `CREATE ROUTINE`, and `ALTER ROUTINE`. Auth, Notification, and Submission require `RELOAD`; Notification and Submission additionally require global `CREATE USER` and global `GRANT OPTION` for their account-provisioning migrations (`scripts/dev/migrate.sh:189-246`).

The migration manifest records the fixed order **`auth → admin → app → notification → submission → post-owner controls`**, dependency checks, per-Owner checksums, and an overall manifest checksum (`scripts/runbooks/owner-migration-manifest.sh:19-26`, `:196-260`). DEV-LOCAL baseline adoption is narrower still: it requires explicit confirmation, is allowlisted only for `auth`, `admin`, and `submission`, and refuses a schema with Flyway history, a bootstrap signature mismatch, or non-zero bootstrap rows (`scripts/dev/migrate.sh:263-315`).

For any authorized live cutover or rollback, the writer boundary is explicit:

- stop and drain **all** writers before changing routes, grants, or schema ownership;
- preserve unconsumed outbox, PEL, and Inbox state;
- use a previously verified descriptor and schema-compatible artifact;
- restore route/grant state only as required, then use `skip_migrations=true` rather than downgrading schema;
- never reopen cross-Owner grants or revive a deleted writer (`docs/operations/database-migrations.md:19-27`, `docs/operations/deployment.md:26-30`).

The repository’s restore command is a disposable verification drill, not a live production rollback mechanism. No production SLO, failover, or recovery-time claim is made here.

## 6. HA Compose boundary

`docker-compose.ha.yml` is an optional stateful reference profile. It includes a MySQL primary/replica shape with binlog/GTID settings and a read-only, `super-read-only` replica, but the file explicitly leaves promotion and application endpoint changes to an operator (`docker-compose.ha.yml:9-17`, `:19-58`). The deployment runbook likewise says the profile is not default production failover and that promotion, endpoint changes, Sentinel-aware clients, and RPO/RTO remain operator work (`docs/operations/deployment.md:41-43`).

**HA Compose is not transparent failover.** It does not change this matrix’s shared-instance connection budgets, does not provide a production recovery proof, and does not turn a disposable restore target into an authorized production target. All restore targets described above remain disposable.

## Evidence level

**Repository Implemented + Disposable Validatable.** Five Owner schema/account contracts and one explicit JDBC pool are evidenced; four JDBC pool budgets and any deployment-wide connection ceiling are missing evidence. Backup encryption, digest/checksum reconciliation, migration metadata, ordered disposable restore, and rollback/quiescence constraints are repository contracts. There is no production database, production SLO, or transparent HA/failover evidence.
