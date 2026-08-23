-- V20260823150000__Align_Auth_Tables_With_Runtime_Contracts.sql
-- Converge Auth-owner tables with the contracts the services actually read
-- and write. The per-owner canonical bootstrap (V20260729140100) was authored
-- from a pre-refactor snapshot:
--   * refresh_tokens predates the secure hash-only token contract
--     (legacy V20260606130000 / V20260727021915): it still stores a plaintext
--     `token` column and lacks is_revoked / rotated_at / rotation chain links.
--   * oauth_provider_identities predates account ownership (V20260724165931):
--     user_id/created_at instead of account_id/linked_at/unlinked_at.
--   * user_permissions lacks grant provenance (granted_by/granted_at) and uses
--     stale action/resource enum sets that reject current values.
-- Backward compatible: columns are added or renamed in place; no applied
-- migration is edited. Existing refresh-token rows are invalidated because
-- hash-only storage cannot accept legacy plaintext tokens.

-- 1) refresh_tokens -> secure hash-only rotation contract.
DELETE FROM `refresh_tokens`;

ALTER TABLE `refresh_tokens`
  DROP INDEX `idx_refresh_tokens_token_hash`,
  DROP COLUMN `token`,
  MODIFY `token_hash` varchar(64) NOT NULL COMMENT 'SHA-256 refresh token hash',
  ADD UNIQUE KEY `refresh_tokens_token_hash_key` (`token_hash`),
  ADD COLUMN `rotated_at` datetime(3) DEFAULT NULL AFTER `created_at`,
  ADD COLUMN `is_revoked` tinyint(1) NOT NULL DEFAULT 0 AFTER `rotated_at`,
  ADD COLUMN `replaced_by_token_id` varchar(40) DEFAULT NULL AFTER `is_revoked`,
  ADD COLUMN `previous_token_id` varchar(40) DEFAULT NULL AFTER `replaced_by_token_id`,
  ADD INDEX `idx_refresh_tokens_family` (`family_id`, `user_id`);

-- 2) oauth_provider_identities -> account ownership contract.
ALTER TABLE `oauth_provider_identities`
  CHANGE COLUMN `user_id` `account_id` varchar(40) NOT NULL,
  CHANGE COLUMN `created_at` `linked_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  ADD COLUMN `unlinked_at` datetime(3) DEFAULT NULL AFTER `linked_at`,
  ADD INDEX `idx_oauth_provider_account` (`account_id`);

-- 3) user_permissions -> grant provenance and current enum sets.
ALTER TABLE `user_permissions`
  MODIFY `action` enum('CREATE','READ','UPDATE','DELETE','MODERATE','PUBLISH','MANAGE_USERS','MANAGE_PERMISSIONS') NOT NULL,
  MODIFY `resource` enum('USER','PROBLEM','CONTEST','SOLUTION','FORUM_POST','FORUM_COMMENT','SYSTEM','PROBLEM_LIST','TAG') NOT NULL,
  ADD COLUMN `granted_by` varchar(40) NOT NULL AFTER `resource`,
  ADD COLUMN `granted_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER `granted_by`,
  ADD UNIQUE KEY `user_permissions_user_id_action_resource_key` (`user_id`, `action`, `resource`),
  ADD INDEX `user_permissions_user_id_idx` (`user_id`);
