-- Invalidate all legacy refresh tokens before removing plaintext storage.
DELETE FROM `refresh_tokens`;

ALTER TABLE `refresh_tokens`
  DROP INDEX `refresh_tokens_token_key`,
  DROP INDEX `refresh_tokens_token_idx`,
  DROP COLUMN `token`,
  MODIFY `token_hash` varchar(64) NOT NULL COMMENT 'SHA-256 refresh token hash',
  ADD UNIQUE KEY `refresh_tokens_token_hash_key` (`token_hash`);

-- Preserve demo data references while making every documented seed credential unusable.
UPDATE `users`
SET
  `password` = '$2a$12$8CuxDkD5rBfP6p8EOrLJauAefkzi1WyQWlLE2nGdI0XXfD3TWJm2e',
  `is_active` = 0,
  `is_banned` = 1,
  `banned_until` = NULL,
  `banned_reason` = 'Disabled security-review seed account',
  `password_reset_token_hash` = NULL,
  `password_reset_expires_at` = NULL
WHERE `username` IN (
  'admin',
  'admin_two',
  'super_root',
  'super_vp',
  'mike_mod',
  'nina_mod',
  'alice_coder',
  'bob_dev',
  'carol_wu',
  'david_chen',
  'eva_zhang',
  'frank_lee'
);
