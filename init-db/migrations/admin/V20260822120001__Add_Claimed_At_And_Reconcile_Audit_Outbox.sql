-- Reconcile admin.audit_outbox with canonical P3-AUDIT-001 shape and add lease for crash recovery.
-- The admin owner table (V20260729140200) used resource_type/resource_id/details/status while
-- the canonical shared migration (V20260728203000) and Java code use entity_type/entity_id/old_values/new_values/ip_address/user_agent/state.
-- This migration adds the missing columns, backfills from legacy columns, and adds lease columns.
-- Claim SQL now requires state, claimed_at, claim_owner; without this the new claim SQL would fail on admin DB.

ALTER TABLE `audit_outbox`
  ADD COLUMN `entity_type` VARCHAR(64) NULL AFTER `action`,
  ADD COLUMN `entity_id` VARCHAR(64) NULL AFTER `entity_type`,
  ADD COLUMN `old_values` JSON NULL AFTER `entity_id`,
  ADD COLUMN `new_values` JSON NULL AFTER `old_values`,
  ADD COLUMN `ip_address` VARCHAR(45) NULL DEFAULT 'unknown' AFTER `new_values`,
  ADD COLUMN `user_agent` VARCHAR(255) NULL AFTER `ip_address`,
  ADD COLUMN `state` VARCHAR(16) NULL AFTER `user_agent`,
  ADD COLUMN `claimed_at` DATETIME(3) DEFAULT NULL AFTER `created_at`,
  ADD COLUMN `claim_owner` VARCHAR(64) DEFAULT NULL AFTER `claimed_at`,
  ADD KEY `idx_state_claimed` (`state`, `claimed_at`);

-- Legacy columns were NOT NULL; canonical inserts omit them (use entity_*), so make them nullable and preserve data.
ALTER TABLE `audit_outbox`
  MODIFY COLUMN `resource_type` VARCHAR(60) NULL,
  MODIFY COLUMN `resource_id` VARCHAR(60) NULL;

-- Backfill new columns from legacy columns where present.
UPDATE `audit_outbox`
SET
  `entity_type` = COALESCE(`entity_type`, `resource_type`),
  `entity_id` = COALESCE(`entity_id`, `resource_id`),
  `state` = COALESCE(`state`,
    CASE `status`
      WHEN 'PENDING' THEN 'PENDING'
      WHEN 'PROCESSED' THEN 'PROCESSED'
      WHEN 'FAILED' THEN 'FAILED'
      ELSE 'PENDING'
    END)
WHERE `state` IS NULL OR `entity_type` IS NULL;

-- Ensure state is not null for future inserts (keep legacy status for backward compat).
ALTER TABLE `audit_outbox`
  MODIFY COLUMN `state` VARCHAR(16) NOT NULL DEFAULT 'PENDING';

-- ------------------------------------------------------------------
-- Reconcile admin.audit_logs with canonical AuditLog shape.
-- Legacy shape (V20260729140200) used resource_type/resource_id/details;
-- canonical Java AuditLog expects entity_type/entity_id/old_values/new_values/ip_address/user_agent.
-- Without this, AuditLogMapper.insert (via AuditOutboxProcessor) fails on admin DB after claim recovery.
ALTER TABLE `audit_logs`
  ADD COLUMN `entity_type` VARCHAR(64) NULL AFTER `action`,
  ADD COLUMN `entity_id` VARCHAR(64) NULL AFTER `entity_type`,
  ADD COLUMN `old_values` JSON NULL AFTER `entity_id`,
  ADD COLUMN `new_values` JSON NULL AFTER `old_values`,
  ADD COLUMN `ip_address` VARCHAR(45) NULL AFTER `new_values`,
  ADD COLUMN `user_agent` VARCHAR(255) NULL AFTER `ip_address`;

-- Legacy columns were NOT NULL; canonical inserts omit them, so make them nullable.
ALTER TABLE `audit_logs`
  MODIFY COLUMN `resource_type` VARCHAR(60) NULL,
  MODIFY COLUMN `resource_id` VARCHAR(60) NULL;

-- Backfill new columns from legacy columns where present.
UPDATE `audit_logs`
SET
  `entity_type` = COALESCE(`entity_type`, `resource_type`),
  `entity_id` = COALESCE(`entity_id`, `resource_id`)
WHERE `entity_type` IS NULL;

-- Keep legacy resource_type/resource_id/details for backward compat; new code uses entity_* and old/new values.
