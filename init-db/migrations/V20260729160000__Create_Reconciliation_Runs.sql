-- V20260729160000__Create_Reconciliation_Runs.sql
-- P5-RECONCILE-001: Reconciliation run history table

CREATE TABLE IF NOT EXISTS `reconciliation_runs` (
  `run_id` varchar(40) NOT NULL,
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` datetime(3) DEFAULT NULL,
  `owner` varchar(20) NOT NULL COMMENT 'Auth/Admin/App/ALL',
  `status` varchar(20) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/COMPLETED/FAILED',
  `divergence_count` int NOT NULL DEFAULT 0,
  `orphan_count` int NOT NULL DEFAULT 0,
  `detail` text COMMENT 'JSON summary of reconciliation results',
  PRIMARY KEY (`run_id`),
  KEY `idx_recon_runs_started_at` (`started_at`),
  KEY `idx_recon_runs_owner` (`owner`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
