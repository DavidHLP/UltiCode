-- TASK-028 cutover reconciliation.
-- Before the durable result dispatcher was enabled, the result-outbox stub could mark
-- terminal rows DELIVERED without creating an integration event. Recreate those events
-- with the source result row ID so replay uses the same idempotency key as the dispatcher.

-- A prior durable publication may have inserted the integration row and later
-- exhausted Redis retries, leaving the source result DELIVERED while the
-- matching integration event is DEAD. Requeue that event instead of treating
-- its existence as successful reconciliation.

-- The pre-cutover listener also recorded Pending/Judging notifications. They
-- are not durable verdict events and must never be published by the new
-- dispatcher. Retire any still-retryable historical rows before reconciliation.
UPDATE `submission_result_outbox`
SET `state` = 'DEAD',
    `last_error` = 'Retired non-terminal result row during TASK-028 cutover',
    `delivered_at` = NULL,
    `claimed_at` = NULL,
    `next_retry_at` = CURRENT_TIMESTAMP(3)
WHERE `state` IN ('PENDING', 'CLAIMED')
  AND `verdict` IN ('Pending', 'Judging');
UPDATE `integration_outbox` i
JOIN `submission_result_outbox` r
  ON i.`aggregate_id` = r.`submission_id`
 AND i.`aggregate_version` = r.`generation`
 AND i.`event_type` = 'SubmissionJudged'
SET i.`event_id` = r.`id`,
    i.`state` = 'PENDING',
    i.`attempts` = 0,
    i.`last_error` = NULL,
    i.`stream_id` = NULL,
    i.`claimed_at` = NULL,
    i.`delivered_at` = NULL,
    i.`next_retry_at` = CURRENT_TIMESTAMP(3)
WHERE r.`state` = 'DELIVERED'
  AND r.`verdict` NOT IN ('Pending', 'Judging')
  AND i.`state` = 'DEAD';

INSERT IGNORE INTO `integration_outbox`
  (`event_id`, `owner`, `aggregate_id`, `aggregate_version`, `event_type`,
   `schema_version`, `payload`, `state`, `attempts`, `created_at`, `next_retry_at`)
SELECT
  r.`id`,
  'App',
  r.`submission_id`,
  r.`generation`,
  'SubmissionJudged',
  1,
  JSON_OBJECT(
    'submissionId', r.`submission_id`,
    'generation', r.`generation`,
    'userId', r.`user_id`,
    'problemId', r.`problem_id`,
    'verdict', r.`verdict`,
    'runtimeMs', r.`runtime_ms`,
    'memoryMb', r.`memory_mb`,
    'contestId', r.`contest_id`
  ),
  'PENDING',
  0,
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3)
FROM `submission_result_outbox` r
WHERE r.`state` = 'DELIVERED'
  AND r.`verdict` NOT IN ('Pending', 'Judging')
  AND NOT EXISTS (
    SELECT 1
    FROM `integration_outbox` i
    WHERE i.`aggregate_id` = r.`submission_id`
      AND i.`aggregate_version` = r.`generation`
      AND i.`event_type` = 'SubmissionJudged'
  );
