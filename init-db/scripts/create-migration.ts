#!/usr/bin/env ts-node
/**
 * Migration Template Generator
 *
 * Creates a new migration directory with idempotent SQL template
 *
 * Usage:
 *   ts-node scripts/create-migration.ts <migration-name>
 *   ts-node scripts/create-migration.ts add_user_settings
 *
 * Options:
 *   --idempotent    Create idempotent version (default)
 *   --plain         Create plain SQL version
 *   --both          Create both versions
 */

import * as fs from 'fs';
import * as path from 'path';

const MIGRATIONS_DIR = path.resolve(__dirname, '../migrations');

interface MigrationOptions {
  name: string;
  idempotent: boolean;
  plain: boolean;
}

function generateTimestamp(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  const hours = String(now.getHours()).padStart(2, '0');
  const minutes = String(now.getMinutes()).padStart(2, '0');
  const seconds = String(now.getSeconds()).padStart(2, '0');
  return `${year}${month}${day}${hours}${minutes}${seconds}`;
}

function toSnakeCase(name: string): string {
  return name
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .replace(/[\s-]+/g, '_')
    .toLowerCase();
}

function generateIdempotentTemplate(migrationName: string, timestamp: string): string {
  const procName = `apply_migration_${timestamp}`;

  return `-- ============================================================================
-- Migration: ${timestamp}_${migrationName}
-- Description: [Add description here]
--
-- IDEMPOTENT migration using stored procedures
-- Can be executed multiple times safely
-- ============================================================================

SET NAMES utf8mb4;
SET @migration_version = '${timestamp}';
SET @migration_name = '${migrationName}';

-- Ensure utilities are available
-- SOURCE migrations/utils/idempotent-helpers.sql;

DROP PROCEDURE IF EXISTS ${procName};

DELIMITER //

CREATE PROCEDURE ${procName}()
BEGIN
    -- Skip if already applied
    IF is_migration_applied(@migration_version) THEN
        SELECT CONCAT('Migration ', @migration_version, ' already applied, skipping.') AS message;
        LEAVE main_block;
    END IF;

    main_block: BEGIN
        -- ====================================================================
        -- Add your migration logic here
        -- ====================================================================

        -- Example: Create table
        -- CALL create_table_if_not_exists('example_table', '
        --     CREATE TABLE \`example_table\` (
        --         \`id\` VARCHAR(40) NOT NULL,
        --         \`name\` VARCHAR(255) NOT NULL,
        --         \`created_at\` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        --         PRIMARY KEY (\`id\`)
        --     ) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        -- ');

        -- Example: Add column
        -- CALL add_column_if_not_exists('example_table', 'description', 'TEXT NULL');

        -- Example: Add index
        -- CALL add_index_if_not_exists('example_table', 'example_table_name_idx', '\`name\`', FALSE);

        -- Example: Add foreign key
        -- CALL add_foreign_key_if_not_exists(
        --     'example_table', 'example_table_user_id_fkey',
        --     '\`user_id\`', 'users', '\`id\`',
        --     'CASCADE', NULL
        -- );

        -- ====================================================================
        -- Record migration
        -- ====================================================================
        CALL record_migration(@migration_version, @migration_name, NULL, NULL);

        SELECT CONCAT('✓ Migration ', @migration_version, ' completed successfully') AS status;

    END main_block;

END //

DELIMITER ;

-- Execute the migration
CALL ${procName}();

-- Clean up
DROP PROCEDURE IF EXISTS ${procName};
`;
}

function generatePlainTemplate(migrationName: string, timestamp: string): string {
  return `-- ============================================================================
-- Migration: ${timestamp}_${migrationName}
-- Description: [Add description here]
--
-- Standard Prisma-compatible migration
-- ============================================================================

-- Add your migration SQL here

-- Example:
-- CREATE TABLE \`example_table\` (
--     \`id\` VARCHAR(40) NOT NULL,
--     \`name\` VARCHAR(255) NOT NULL,
--     \`created_at\` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
--     PRIMARY KEY (\`id\`)
-- ) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
`;
}

function generateRollbackTemplate(migrationName: string, timestamp: string): string {
  return `-- ============================================================================
-- Rollback: ${timestamp}_${migrationName}
-- ============================================================================

-- Add your rollback SQL here

-- Example:
-- DROP TABLE IF EXISTS \`example_table\`;
`;
}

function createMigration(options: MigrationOptions): void {
  const timestamp = generateTimestamp();
  const snakeName = toSnakeCase(options.name);
  const dirName = `${timestamp}_${snakeName}`;
  const dirPath = path.join(MIGRATIONS_DIR, dirName);

  // Create migration directory
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath, { recursive: true });
  }

  // Create migration files
  if (options.idempotent || options.plain === false) {
    const idempotentFile = path.join(dirPath, 'migration.idempotent.sql');
    fs.writeFileSync(idempotentFile, generateIdempotentTemplate(snakeName, timestamp));
    console.log(`✓ Created: ${idempotentFile}`);
  }

  if (options.plain) {
    const plainFile = path.join(dirPath, 'migration.sql');
    fs.writeFileSync(plainFile, generatePlainTemplate(snakeName, timestamp));
    console.log(`✓ Created: ${plainFile}`);
  }

  // Always create rollback file
  const rollbackFile = path.join(dirPath, 'rollback.sql');
  fs.writeFileSync(rollbackFile, generateRollbackTemplate(snakeName, timestamp));
  console.log(`✓ Created: ${rollbackFile}`);

  console.log(`\n📁 Migration directory: ${dirPath}`);
  console.log(`\nNext steps:`);
  console.log(`  1. Edit the migration file(s)`);
  console.log(`  2. Run: ts-node scripts/run-migration.ts ${dirName}`);
}

function main(): void {
  const args = process.argv.slice(2);

  if (args.length === 0 || args.includes('--help')) {
    console.log(`
Usage:
  ts-node scripts/create-migration.ts <migration-name>

Options:
  --idempotent    Create idempotent version (default)
  --plain         Create plain SQL version
  --both          Create both versions

Examples:
  ts-node scripts/create-migration.ts add_user_settings
  ts-node scripts/create-migration.ts add_user_settings --both
`);
    process.exit(0);
  }

  const name = args.find(arg => !arg.startsWith('--'))!;
  const idempotent = args.includes('--idempotent') || (!args.includes('--plain') && !args.includes('--both'));
  const plain = args.includes('--plain') || args.includes('--both');

  createMigration({ name, idempotent, plain });
}

main();
