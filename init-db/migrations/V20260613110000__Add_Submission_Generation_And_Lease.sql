-- ADR-003 M3b: Add generation fence + JUDGING lease columns to submissions.
-- Generation fence + lease: see submission/fence/SubmissionStateMachine + LeaseConstants + JudgingLeaseReaper.
-- generation BIGINT NOT NULL DEFAULT 1: old code never reads this column; M3b fence CAS
--   requires a definite value. Historical rows backfilled to 1 by the column default
--   (no separate UPDATE needed). Deviates from strict expand-contract (nullable first),
--   documented in plan §1 — acceptable because nothing reads it until M3b fence is on.
-- current_attempt_id / judging_lease_expires_at NULLABLE: populated only while JUDGING.
ALTER TABLE `submissions`
  ADD COLUMN `generation` bigint NOT NULL DEFAULT 1,
  ADD COLUMN `current_attempt_id` varchar(40) DEFAULT NULL,
  ADD COLUMN `judging_lease_expires_at` datetime(3) DEFAULT NULL,
  ADD KEY `idx_lease_expiry` (`status`, `judging_lease_expires_at`);
