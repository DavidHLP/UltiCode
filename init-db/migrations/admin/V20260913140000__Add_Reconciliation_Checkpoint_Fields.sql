-- P7-RECON-AGGREGATOR-001: index durable reconciliation checkpoint identity.
-- Existing detail JSON remains the continuation payload; these columns make
-- checkpoint selection bounded and independent of text-pattern matching.

ALTER TABLE `reconciliation_runs`
  ADD COLUMN `scan_mode` varchar(20) DEFAULT NULL AFTER `owner`,
  ADD COLUMN `scan_created_since` datetime(3) DEFAULT NULL AFTER `scan_mode`,
  ADD KEY `idx_recon_runs_checkpoint`
    (`owner`, `status`, `scan_mode`, `scan_created_since`, `started_at`, `run_id`);

-- Preserve in-flight checkpoints created before the indexed columns existed.
-- Rows with an unrecognised or malformed continuation stay unindexed and are
-- ignored rather than being trusted as a resumable checkpoint.
UPDATE `reconciliation_runs`
SET `scan_mode` = JSON_UNQUOTE(JSON_EXTRACT(`detail`, '$.mode')),
    `scan_created_since` = CASE
      WHEN JSON_UNQUOTE(JSON_EXTRACT(`detail`, '$.mode')) = 'INCREMENTAL'
       AND JSON_TYPE(JSON_EXTRACT(`detail`, '$.continuation.createdSince')) = 'STRING'
       AND JSON_UNQUOTE(JSON_EXTRACT(`detail`, '$.continuation.createdSince'))
           REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2}([.][0-9]{1,9})?)?$'
      THEN CAST(REPLACE(
          JSON_UNQUOTE(JSON_EXTRACT(`detail`, '$.continuation.createdSince')), 'T', ' ')
          AS DATETIME(3))
      ELSE NULL
    END
WHERE `scan_mode` IS NULL
  AND `detail` IS NOT NULL
  AND JSON_VALID(`detail`)
  AND JSON_UNQUOTE(JSON_EXTRACT(`detail`, '$.mode')) IN ('FULL', 'INCREMENTAL')
  AND (`status` = 'COMPLETED'
       OR JSON_TYPE(JSON_EXTRACT(`detail`, '$.continuation')) = 'OBJECT');
