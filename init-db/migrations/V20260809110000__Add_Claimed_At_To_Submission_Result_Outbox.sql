-- TASK-028: claim lease timestamp for crash recovery.
ALTER TABLE `submission_result_outbox`
  ADD COLUMN `claimed_at` datetime(3) DEFAULT NULL
  AFTER `created_at`;
