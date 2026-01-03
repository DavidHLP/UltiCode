-- DropIndex
DROP INDEX `contest_rankings_contest_id_user_id_key` ON `contest_rankings`;

-- CreateIndex
CREATE UNIQUE INDEX `contest_rankings_contest_id_user_id_is_virtual_key` ON `contest_rankings`(`contest_id`, `user_id`, `is_virtual`);
