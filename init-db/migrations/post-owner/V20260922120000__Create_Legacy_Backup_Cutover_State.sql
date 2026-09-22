-- Persist the legacy backup cutover fence used by the post-owner
-- reconciliation runbook.  The row is deliberately owned by Admin but lives
-- in the privileged post-owner chain because it describes two schemas.

CREATE TABLE IF NOT EXISTS `admin`.`backup_cutover_state` (
    `id` tinyint unsigned NOT NULL,
    `source_row_count` bigint unsigned NOT NULL DEFAULT 0,
    `target_row_count` bigint unsigned NOT NULL DEFAULT 0,
    `cutover_completed_at` datetime(3) DEFAULT NULL,
    `last_reconciled_at` datetime(3) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `admin`.`backup_cutover_state` (
    `id`, `source_row_count`, `target_row_count`,
    `cutover_completed_at`, `last_reconciled_at`
) VALUES (1, 0, 0, NULL, NULL)
ON DUPLICATE KEY UPDATE `id` = `id`;
