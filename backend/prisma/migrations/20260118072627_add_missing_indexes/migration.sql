-- CreateIndex
CREATE INDEX `contest_participants_user_id_status_is_virtual_idx` ON `contest_participants`(`user_id`, `status`, `is_virtual`);

-- CreateIndex
CREATE INDEX `forum_comments_post_id_created_at_idx` ON `forum_comments`(`post_id`, `created_at`);

-- CreateIndex
CREATE INDEX `forum_posts_is_deleted_created_at_idx` ON `forum_posts`(`is_deleted`, `created_at`);

-- CreateIndex
CREATE INDEX `forum_posts_community_id_created_at_idx` ON `forum_posts`(`community_id`, `created_at`);

-- CreateIndex
CREATE INDEX `problems_is_published_is_deleted_idx` ON `problems`(`is_published`, `is_deleted`);

-- CreateIndex
CREATE INDEX `problems_is_flagged_is_deleted_idx` ON `problems`(`is_flagged`, `is_deleted`);

-- CreateIndex
CREATE INDEX `solution_comments_solution_id_created_at_idx` ON `solution_comments`(`solution_id`, `created_at`);

-- CreateIndex
CREATE INDEX `solutions_problem_id_created_at_idx` ON `solutions`(`problem_id`, `created_at`);

-- CreateIndex
CREATE INDEX `solutions_user_id_created_at_idx` ON `solutions`(`user_id`, `created_at`);

-- CreateIndex
CREATE INDEX `solutions_is_flagged_is_deleted_idx` ON `solutions`(`is_flagged`, `is_deleted`);

-- CreateIndex
CREATE INDEX `solutions_is_published_is_deleted_idx` ON `solutions`(`is_published`, `is_deleted`);

-- CreateIndex
CREATE INDEX `submissions_created_at_idx` ON `submissions`(`created_at`);

-- CreateIndex
CREATE INDEX `submissions_user_id_status_created_at_idx` ON `submissions`(`user_id`, `status`, `created_at`);

-- CreateIndex
CREATE INDEX `users_is_active_last_login_at_idx` ON `users`(`is_active`, `last_login_at`);

-- CreateIndex
CREATE INDEX `users_joined_at_idx` ON `users`(`joined_at`);
