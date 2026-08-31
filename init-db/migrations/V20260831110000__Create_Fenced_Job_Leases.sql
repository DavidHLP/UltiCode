-- P3-LEASE-001: shared persistent lease rows for cross-replica singleton work.
-- The database clock is authoritative; fence_token increases after expiry so
-- a stale runner cannot pass a later completion CAS.

CREATE TABLE IF NOT EXISTS `admin`.`fenced_job_leases` (
  `lease_name` varchar(120) NOT NULL,
  `fence_token` bigint NOT NULL,
  `owner_token` varchar(120) DEFAULT NULL,
  `leased_until` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`lease_name`),
  KEY `idx_fenced_job_leases_expiry` (`leased_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

GRANT SELECT, INSERT, UPDATE, DELETE ON `admin`.`fenced_job_leases` TO 'admin_rw'@'%';
