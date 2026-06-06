# UltiCode Database Migrations

`init-db/migrations/` is the canonical Flyway migration source. Database credentials
are never stored in this directory; local commands load the private root `.env`.

## Local Usage

From the repository root:

```bash
./scripts/dev/init-env.sh
./scripts/dev/migrate.sh info
./scripts/dev/migrate.sh migrate
./scripts/dev/migrate.sh validate
```

`scripts/dev/up.sh` automatically runs `migrate` before starting the applications.

## Configuration

The Maven Flyway plugin reads:

```text
DB_HOST
DB_PORT
DB_USER
DB_PASSWORD
DB_NAME
```

`scripts/dev/migrate.sh` exports these values from the root `.env` and runs Maven
from this directory. CI/CD supplies the same values through its secret environment
or Flyway container arguments.

## Creating a Migration

Use an increasing timestamp:

```bash
touch init-db/migrations/V20260606160000__Add_New_Feature.sql
```

Rules:

1. Never modify a migration that may already be applied.
2. Add a later migration for every schema or data correction.
3. Keep `V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql`
   after all historical public-password seed migrations.
4. Do not add usable default accounts or plaintext credentials.
5. Use MySQL-compatible, repeatable data updates where rollback is not available.

Run `./scripts/dev/migrate.sh validate` after adding a migration and validate the
complete chain against a fresh MySQL database before release.
