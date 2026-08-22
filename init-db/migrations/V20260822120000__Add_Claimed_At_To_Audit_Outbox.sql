-- Add lease timestamp and owner for audit outbox crash recovery (P3-AUDIT-001)
-- claim() sets claimed_at + claim_owner; stale PROCESSING rows are reclaimed to PENDING
-- and terminal updates fence on claim_owner to prevent late-worker duplicates.
ALTER TABLE `audit_outbox`
  ADD COLUMN `claimed_at` DATETIME(3) DEFAULT NULL AFTER `created_at`,
  ADD COLUMN `claim_owner` VARCHAR(64) DEFAULT NULL AFTER `claimed_at`,
  ADD KEY `idx_state_claimed` (`state`, `claimed_at`);
