-- Store durable backup objects in the shared private object store.
-- Existing rows retain nullable columns until the data migration backfills object references.
ALTER TABLE `backups`
  ADD COLUMN `object_key` varchar(512) NULL AFTER `filename`,
  ADD COLUMN `checksum` char(64) NULL AFTER `size`;
