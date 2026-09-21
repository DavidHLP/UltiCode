-- The deletion tombstone already records that an Admin backup row is gone.
-- These columns extend it into the durable cleanup intent for the dump bytes:
-- the row that carried the object key is deleted, so an interrupted or failed
-- object deletion would otherwise orphan the dump in the bucket forever.
-- `object_deleted_at` stays NULL until a deletion succeeds and is what the
-- retrying sweep selects on.

ALTER TABLE `admin`.`backup_deletion_tombstones`
    ADD COLUMN `object_key` varchar(512) DEFAULT NULL,
    ADD COLUMN `object_deleted_at` datetime(3) DEFAULT NULL,
    ADD COLUMN `cleanup_attempts` int NOT NULL DEFAULT 0,
    ADD COLUMN `cleanup_error` varchar(500) DEFAULT NULL;
