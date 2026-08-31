-- P3-LEASE-001: retain the fencing generation on each Admin reconciliation run.
-- Existing history remains readable; new runs always persist the current token.

ALTER TABLE `reconciliation_runs`
  ADD COLUMN `fence_token` bigint NOT NULL DEFAULT '0' AFTER `owner`;
