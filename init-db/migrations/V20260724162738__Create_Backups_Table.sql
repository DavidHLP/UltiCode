-- Create canonical `backups` table for /admin/backups.
-- ------------------------------------------------------------
-- Background: backend-admin/src/main/java/com/ulticode/modules/backup/**
--   (Backup entity with @TableName("backups"), BackupMapper, BackupService,
--   BackupController, BackupReadProjection) reads and writes the `backups`
--   table, but `init-db/migrations/` never defines it. CI does not allow
--   ORM auto-DDL, so on a fresh migrate the runtime hits "Table
--   'ulticode.backups' doesn't exist" on the first admin backup call.
--
-- This migration is the canonical, additive source-of-truth for the
-- `backups` table. It is intentionally NOT a `CREATE TABLE IF NOT EXISTS`:
-- the table never existed in any prior migration, so any pre-existing
-- database that does not have this table is in the drift described in
-- PROJECT_DOCUMENTATION.md §5.1 and gets a fresh, well-typed table
-- here.
--
-- Column set mirrors the Backup entity field-by-field:
--   id          varchar(40)   PK         — @TableId ASSIGN_UUID
--   filename    varchar(255)  NOT NULL
--   size        bigint        NOT NULL   — service updates as dump progresses
--   type        enum('FULL','INCREMENTAL') NOT NULL
--   status      enum('PENDING','IN_PROGRESS','COMPLETED','FAILED') NOT NULL
--   created_by  varchar(40)   NOT NULL   — operator principal (auth account id)
--   created_at  datetime(3)   NOT NULL   — auto-fill on INSERT
--   completed_at datetime(3)  NULL      — set when dump lifecycle terminates
--   metadata    JSON          NULL      — JacksonTypeHandler dump context
--   error       text          NULL      — failure detail when status=FAILED
--
-- Indexes:
--   - PK (id) for point lookups in BackupServiceImpl.getBackupFile
--   - idx_status_created_at (status, created_at DESC) for the
--     "most-recent backup per status" Admin dashboard query
--   - idx_created_by (created_by) for per-operator listing
--
-- Foreign keys: intentionally NONE. `created_by` references an auth-owned
-- `users.id` value but the FK is not enforced at this stage because Phase 5
-- will physically split the schemas; adding a cross-schema FK now would
-- block that move. The application layer enforces referential integrity.
--
-- Rollback: DROP TABLE backups; perform a mysqldump backup BEFORE applying
-- the migration if any pre-existing data is suspected. Per guide §15, no
-- application-level reverse path is provided.
-- ------------------------------------------------------------

CREATE TABLE `backups` (
  `id` varchar(40) NOT NULL,
  `filename` varchar(255) NOT NULL,
  `size` bigint NOT NULL DEFAULT '0',
  `type` enum('FULL','INCREMENTAL') NOT NULL,
  `status` enum('PENDING','IN_PROGRESS','COMPLETED','FAILED') NOT NULL,
  `created_by` varchar(40) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `completed_at` datetime(3) DEFAULT NULL,
  `metadata` JSON DEFAULT NULL,
  `error` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status_created_at` (`status`, `created_at`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;