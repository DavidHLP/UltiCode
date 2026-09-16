-- P3-AUDIT-001: retain Admin audit-outbox failure evidence and retry timing.
-- The existing PROCESSING/PROCESSED/FAILED state vocabulary is preserved.
-- A FAILED row is retryable when attempts < 5 and next_retry_at <= NOW(3);
-- attempts >= 5 is the terminal FAILED form and is excluded by claim SQL.
-- Keep legacy status/resource columns untouched for mixed-version compatibility.
ALTER TABLE `audit_outbox`
  ADD COLUMN `attempts` int NOT NULL DEFAULT 0 AFTER `state`,
  ADD COLUMN `last_error` varchar(500) DEFAULT NULL AFTER `attempts`,
  ADD COLUMN `next_retry_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER `claim_owner`,
  ADD KEY `idx_audit_outbox_state_retry` (`state`, `next_retry_at`, `attempts`);
