-- Durable cleanup intents for Admin-uploaded objects that never reached their
-- owner row (App rejected the write, or App was unavailable). The request that
-- created the object records the key, and the sweep deletes it idempotently, so
-- a retried upload cannot leak one object per attempt while storage is down.

CREATE TABLE IF NOT EXISTS `admin`.`storage_cleanup_outbox` (
    `object_key` varchar(512) NOT NULL,
    `attempts` int NOT NULL DEFAULT 0,
    `last_error` varchar(500) DEFAULT NULL,
    `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `deleted_at` datetime(3) DEFAULT NULL,
    PRIMARY KEY (`object_key`),
    KEY `idx_admin_storage_cleanup_pending` (`deleted_at`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
