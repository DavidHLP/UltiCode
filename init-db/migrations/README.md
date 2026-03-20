# Idempotent Migration System

This directory contains the database migration system for UltiCode, featuring both Prisma-managed migrations and idempotent SQL migrations for CI/CD environments.

## Directory Structure

```
migrations/
├── 00000000000000_create_schema_migrations/    # Version control table
├── 20260320000000_add_moderation_tables/       # Example: Moderation system
│   ├── migration.sql                           # Prisma standard format
│   ├── migration.idempotent.sql                # Idempotent version (stored procedure)
│   └── rollback.sql                            # Rollback script
├── utils/
│   └── idempotent-helpers.sql                  # Reusable helper functions
├── migration_lock.toml                         # Prisma lock file
└── migrate-forum-tags.ts                       # Data migration script
```

## Two Migration Approaches

### 1. Prisma Migrations (Recommended for Development)

```bash
# Create and apply migration
pnpm prisma:migrate

# Deploy to production
pnpm prisma:migrate:prod
```

**Pros:**
- Automatic version tracking in `_prisma_migrations` table
- Integrated with Prisma schema
- Easy rollback with `prisma migrate resolve`

**Cons:**
- Requires Prisma CLI
- SQL files are not idempotent (cannot be re-run)

### 2. Idempotent Migrations (Recommended for CI/CD)

```bash
# Run all pending migrations
pnpm migration:run:all

# Run specific migration
pnpm migration:run migrations/20260320000000_add_moderation_tables

# Check status
pnpm migration:status

# Dry run (preview without executing)
pnpm migration:run:dry
```

**Pros:**
- Can be executed multiple times safely
- No dependency on Prisma CLI
- Works with any MySQL client
- Detailed logging and checksum verification

**Cons:**
- More verbose SQL (stored procedure wrapper)
- Requires `schema_migrations` table

## Creating a New Migration

```bash
# Create idempotent migration (default)
pnpm migration:create add_user_settings

# Create plain SQL migration
pnpm migration:create add_user_settings --plain

# Create both versions
pnpm migration:create add_user_settings --both
```

## Idempotent Migration Pattern

```sql
-- Template for idempotent migrations
SET @migration_version = '20260321000000';
SET @migration_name = 'example_migration';

DROP PROCEDURE IF EXISTS apply_migration_20260321000000;

DELIMITER //
CREATE PROCEDURE apply_migration_20260321000000()
BEGIN
    -- Check if already applied
    IF is_migration_applied(@migration_version) THEN
        LEAVE main_block;
    END IF;

    main_block: BEGIN
        -- Create table with check
        CALL create_table_if_not_exists('my_table', '
            CREATE TABLE `my_table` (
                `id` VARCHAR(40) NOT NULL,
                `name` VARCHAR(255) NOT NULL,
                PRIMARY KEY (`id`)
            ) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        ');

        -- Add column with check
        CALL add_column_if_not_exists('my_table', 'description', 'TEXT NULL');

        -- Add index with check
        CALL add_index_if_not_exists('my_table', 'my_table_name_idx', '`name`', FALSE);

        -- Add foreign key with check
        CALL add_foreign_key_if_not_exists(
            'my_table', 'my_table_user_id_fkey',
            '`user_id`', 'users', '`id`',
            'CASCADE', NULL
        );

        -- Record migration
        CALL record_migration(@migration_version, @migration_name, NULL, NULL);
    END main_block;
END //
DELIMITER ;

CALL apply_migration_20260321000000();
DROP PROCEDURE IF EXISTS apply_migration_20260321000000;
```

## Helper Functions

The `utils/idempotent-helpers.sql` file provides these reusable functions:

| Function | Description |
|----------|-------------|
| `is_migration_applied(version)` | Check if migration was applied |
| `table_exists(table_name)` | Check if table exists |
| `column_exists(table, column)` | Check if column exists |
| `index_exists(table, index_name)` | Check if index exists |
| `foreign_key_exists(table, constraint)` | Check if FK exists |

| Procedure | Description |
|-----------|-------------|
| `create_table_if_not_exists(name, sql)` | Create table safely |
| `add_column_if_not_exists(table, col, def)` | Add column safely |
| `add_index_if_not_exists(table, name, cols, unique)` | Add index safely |
| `add_foreign_key_if_not_exists(...)` | Add FK safely |
| `record_migration(version, name, checksum, time)` | Record migration |

## Version Control Tables

### `_prisma_migrations` (Prisma managed)
- Tracks Prisma migration status
- Used by `prisma migrate` commands

### `schema_migrations` (Manual)
- Tracks idempotent migration status
- Includes checksum verification
- Records execution time and user

## Best Practices

1. **Use Prisma migrations for schema changes during development**
2. **Use idempotent migrations for production deployments**
3. **Always include rollback.sql**
4. **Test migrations on a copy of production data**
5. **Never modify applied migrations - create new ones instead**

## Troubleshooting

### Migration already applied but table missing

```sql
-- Remove record and re-run
DELETE FROM schema_migrations WHERE version = '20260320000000';
```

### Checksum mismatch warning

This means the migration file was modified after being applied. Review changes carefully before re-running.

### Foreign key constraint fails

Ensure referenced tables are created before the tables that reference them.
