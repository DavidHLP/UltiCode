-- Preserve Admin-owned deletions while the legacy backup row remains as
-- rollback evidence. Reconciliation must not recreate a deliberately
-- deleted target row from that retained source row.

CREATE TABLE IF NOT EXISTS `admin`.`backup_deletion_tombstones` (
    `backup_id` varchar(40) NOT NULL,
    `deleted_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`backup_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
