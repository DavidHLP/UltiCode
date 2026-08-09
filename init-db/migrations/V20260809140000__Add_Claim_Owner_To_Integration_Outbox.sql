-- TASK-027/TASK-028: identify the dispatcher that owns an integration outbox lease.
-- A stale CLAIMED row may be reclaimed by another instance; confirmation and
-- failure updates must be fenced to the current claim owner.
ALTER TABLE `integration_outbox`
  ADD COLUMN `claim_owner` varchar(80) DEFAULT NULL
  COMMENT 'Dispatcher lease owner'
  AFTER `claimed_at`;

CREATE INDEX `idx_integration_outbox_claim_owner`
  ON `integration_outbox` (`state`, `claim_owner`, `created_at`);
