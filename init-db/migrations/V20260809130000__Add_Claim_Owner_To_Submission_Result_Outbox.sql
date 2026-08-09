-- V20260809130000__Add_Claim_Owner_To_Submission_Result_Outbox.sql
-- TASK-028: isolate result-outbox claims between concurrent dispatcher instances.

ALTER TABLE `submission_result_outbox`
  ADD COLUMN `claim_owner` varchar(80) DEFAULT NULL
  COMMENT 'Dispatcher lease owner'
  AFTER `claimed_at`;

CREATE INDEX `idx_submission_result_outbox_claim_owner`
  ON `submission_result_outbox` (`state`, `claim_owner`, `created_at`);
