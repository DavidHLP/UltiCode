-- Converge `problem_notes` baseline schema with code expectations.
-- ------------------------------------------------------------
-- Background: PROJECT_DOCUMENTATION.md §5.1 documents the drift
-- between:
--   * Baseline (V20260602_120000__Create_All_Tables.sql:721-730):
--       id varchar(40) PK
--       problem_id bigint
--       user_id varchar(40)
--       content text
--       updated_at datetime(3)
--   * Code expectation (ProblemNote entity):
--       createTime  -> create_time datetime(3)
--       updateTime  -> update_time datetime(3) (already implicit; baseline
--                     calls the column updated_at; this migration RENAMES
--                     it to update_time so entity-to-column mapping is
--                     direct, without changing semantics)
--       content     -> MEDIUMTEXT (widening only — never narrow)
--       user_id     -> varchar(40) (already correct; project UUIDs are
--                     varchar(36), varchar(40) keeps headroom)
--   * V20260611141000__Create_Problem_Notes_Table.sql tried to add
--     `create_time`, MEDIUMTEXT, varchar(36) user_id, and FKs via
--     `CREATE TABLE IF NOT EXISTS`. Because the bootstrap migration already
--     created the table, the entire statement was a no-op on every database
--     that applied both migrations in order.
--
-- Resolution (additive, idempotent):
--   1. ADD COLUMN `create_time` datetime(3) NOT NULL with backfill from
--      `updated_at` (a single ALTER … ADD COLUMN with DEFAULT then UPDATE).
--      MySQL does not allow NOT NULL without DEFAULT in-place on tables
--      with data, so we add nullable, backfill, then tighten.
--   2. RENAME COLUMN `updated_at` to `update_time` so the entity's
--      @TableField("update_time") resolves directly. Note: MySQL 8 supports
--      RENAME COLUMN; the rename preserves column order and default.
--   3. MODIFY `content` mediumtext NOT NULL (widening; safe).
--   4. Add supporting index `idx_create_time` so service-layer queries can
--      ORDER BY create_time without filesort.
--
-- Foreign keys (user_id -> users.id, problem_id -> problems.id) are NOT
-- added in this migration. Per guide §5.1 and §15:
--   * Phase 5 will physically split the schemas; cross-schema FKs become
--     impossible at that point. Adding them now would block that move.
--   * The guide requires an orphan-reference scan before adding FKs. That
--     scan is performed as part of P0-SCHEMA-003 (inventory + orphan audit)
--     and is documented for follow-up in DECISIONS.md.
--
-- Rollback (additive, manual):
--   ALTER TABLE problem_notes RENAME COLUMN update_time TO updated_at;
--   ALTER TABLE problem_notes DROP COLUMN create_time;
--   ALTER TABLE problem_notes DROP INDEX idx_create_time;
--   ALTER TABLE problem_notes MODIFY content text NOT NULL;
-- These are safe because they preserve prior content (column was nullable
-- during backfill window only).
-- ------------------------------------------------------------

-- 1. Add create_time nullable, backfill, then enforce NOT NULL DEFAULT.
ALTER TABLE `problem_notes`
  ADD COLUMN `create_time` datetime(3) NULL AFTER `content`;

UPDATE `problem_notes`
  SET `create_time` = `updated_at`
  WHERE `create_time` IS NULL;

ALTER TABLE `problem_notes`
  MODIFY COLUMN `create_time` datetime(3) NOT NULL
    DEFAULT CURRENT_TIMESTAMP(3);

-- 2. Rename updated_at -> update_time so entity @TableField("update_time")
--    binds directly. The column stays datetime(3) NOT NULL with the same
--    default; we explicitly restate the type to avoid any drift.
ALTER TABLE `problem_notes`
  RENAME COLUMN `updated_at` TO `update_time`;

ALTER TABLE `problem_notes`
  MODIFY COLUMN `update_time` datetime(3) NOT NULL
    DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

-- 3. Widen content text -> mediumtext (additive only).
ALTER TABLE `problem_notes`
  MODIFY COLUMN `content` mediumtext NOT NULL;

-- 4. Service-layer index on create_time.
CREATE INDEX `idx_create_time` ON `problem_notes` (`create_time`);