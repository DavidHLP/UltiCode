-- P1-DATA-001: durable proof handoff for the separately invoked contraction migration.
-- The normal shared chain only creates this append-only control table. It never
-- drops legacy data; owner-schema-contraction.sh records a verified snapshot
-- after the owner read/write cutovers and the explicit contraction command
-- consumes that proof from init-db/flyway-contraction.conf.

CREATE TABLE IF NOT EXISTS `owner_contraction_proof` (
  `owner` varchar(32) NOT NULL,
  `source_schema` varchar(64) NOT NULL,
  `target_schema` varchar(64) NOT NULL,
  `source_rows` bigint NOT NULL,
  `target_rows` bigint NOT NULL,
  `snapshot_hash` char(64) NOT NULL,
  `app_account` varchar(128) NOT NULL,
  `app_dml_grants` int NOT NULL,
  `backup_reference` varchar(255) NOT NULL,
  `backup_verified_at` datetime(3) NOT NULL,
  `writers_quiesced_at` datetime(3) NOT NULL,
  `verified_at` datetime(3) NOT NULL,
  `verified_by` varchar(128) NOT NULL,
  PRIMARY KEY (`owner`),
  KEY `idx_owner_contraction_proof_verified_at` (`verified_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
