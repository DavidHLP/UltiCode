-- CreateTable
CREATE TABLE `users` (
    `id` VARCHAR(40) NOT NULL,
    `username` VARCHAR(120) NOT NULL,
    `name` VARCHAR(120) NULL,
    `email` VARCHAR(255) NULL,
    `avatar` VARCHAR(255) NULL,

    UNIQUE INDEX `users_username_key`(`username`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problems` (
    `id` BIGINT NOT NULL,
    `slug` VARCHAR(120) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `difficulty` ENUM('Easy', 'Medium', 'Hard') NOT NULL,
    `acceptance_rate` DECIMAL(5, 2) NOT NULL DEFAULT 0,
    `status` ENUM('solved', 'attempted', 'todo') NOT NULL DEFAULT 'todo',
    `is_premium` BOOLEAN NOT NULL DEFAULT false,
    `has_solution` BOOLEAN NOT NULL DEFAULT false,
    `completed_time` DATE NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_details` (
    `id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `slug` VARCHAR(120) NOT NULL,
    `summary` TEXT NOT NULL,
    `companies` JSON NULL,
    `likes` INTEGER NOT NULL DEFAULT 0,
    `dislikes` INTEGER NOT NULL DEFAULT 0,
    `difficulty_rating` DECIMAL(5, 1) NOT NULL DEFAULT 1500,
    `updated_at` DATETIME(3) NOT NULL,
    `follow_up` TEXT NULL,
    `constraints_json` JSON NOT NULL,
    `hints` JSON NULL,

    UNIQUE INDEX `problem_details_problem_id_key`(`problem_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_tags` (
    `id` VARCHAR(40) NOT NULL,
    `label` VARCHAR(120) NOT NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_tag_relations` (
    `problem_id` BIGINT NOT NULL,
    `tag_id` VARCHAR(40) NOT NULL,

    PRIMARY KEY (`problem_id`, `tag_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_languages` (
    `id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `label` VARCHAR(50) NOT NULL,
    `value` VARCHAR(50) NOT NULL,
    `starter_code` TEXT NOT NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_examples` (
    `id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `example_order` INTEGER NOT NULL DEFAULT 0,
    `input_text` TEXT NOT NULL,
    `output_text` TEXT NOT NULL,
    `explanation` TEXT NULL,
    `inputs` JSON NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `contests` (
    `id` VARCHAR(40) NOT NULL,
    `title` VARCHAR(120) NOT NULL,
    `slug` VARCHAR(120) NOT NULL,
    `contest_type` ENUM('weekly', 'biweekly', 'special') NOT NULL,
    `start_time` DATETIME(3) NOT NULL,
    `duration_minutes` INTEGER NOT NULL,
    `status` ENUM('upcoming', 'running', 'finished') NOT NULL,
    `registered_count` INTEGER NOT NULL DEFAULT 0,
    `participant_count` INTEGER NOT NULL DEFAULT 0,
    `is_rated` BOOLEAN NOT NULL DEFAULT true,
    `description` TEXT NULL,
    `cover_image` VARCHAR(255) NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `contest_problems` (
    `id` VARCHAR(40) NOT NULL,
    `contest_id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `problem_index` VARCHAR(10) NOT NULL,
    `score` INTEGER NOT NULL DEFAULT 0,
    `solved_count` INTEGER NOT NULL DEFAULT 0,
    `submission_count` INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `forum_communities` (
    `id` VARCHAR(40) NOT NULL,
    `name` VARCHAR(120) NOT NULL,
    `slug` VARCHAR(60) NOT NULL,
    `description` TEXT NOT NULL,
    `members` INTEGER NOT NULL DEFAULT 0,
    `online` INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `forum_users` (
    `username` VARCHAR(60) NOT NULL,
    `avatar` VARCHAR(255) NULL,
    `karma` INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (`username`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `forum_posts` (
    `id` VARCHAR(40) NOT NULL,
    `community_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(60) NOT NULL,
    `permalink` VARCHAR(255) NULL,
    `title` VARCHAR(255) NOT NULL,
    `flair_type` ENUM('announcement', 'discussion', 'showcase', 'question', 'hiring') NULL,
    `flair_label` VARCHAR(60) NULL,
    `tags` JSON NOT NULL,
    `excerpt` TEXT NULL,
    `media` JSON NULL,
    `recommendation` JSON NULL,
    `vote_state` ENUM('upvoted', 'downvoted', 'neutral') NOT NULL DEFAULT 'neutral',
    `is_saved` BOOLEAN NOT NULL DEFAULT false,
    `impressions` INTEGER NOT NULL DEFAULT 0,
    `is_pinned` BOOLEAN NOT NULL DEFAULT false,
    `is_locked` BOOLEAN NOT NULL DEFAULT false,
    `created_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `forum_comments` (
    `id` VARCHAR(40) NOT NULL,
    `post_id` VARCHAR(40) NOT NULL,
    `parent_id` VARCHAR(40) NULL,
    `author_id` VARCHAR(60) NOT NULL,
    `body` TEXT NOT NULL,
    `markdown` TEXT NULL,
    `upvotes` INTEGER NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL,
    `edited_at` DATETIME(3) NULL,
    `is_pinned` BOOLEAN NOT NULL DEFAULT false,
    `is_locked` BOOLEAN NOT NULL DEFAULT false,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_list_groups` (
    `id` VARCHAR(40) NOT NULL,
    `name` VARCHAR(120) NOT NULL,
    `sort_order` INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_lists` (
    `id` VARCHAR(40) NOT NULL,
    `group_id` VARCHAR(40) NOT NULL,
    `name` VARCHAR(120) NOT NULL,
    `description` TEXT NULL,
    `author_id` VARCHAR(40) NOT NULL,
    `is_public` BOOLEAN NOT NULL DEFAULT true,
    `created_at` DATETIME(3) NOT NULL,
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `solutions` (
    `id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `content` TEXT NOT NULL,
    `summary` TEXT NULL,
    `language` VARCHAR(50) NOT NULL,
    `tags` JSON NULL,
    `views` INTEGER NOT NULL DEFAULT 0,
    `likes` INTEGER NOT NULL DEFAULT 0,
    `dislikes` INTEGER NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `solution_comments` (
    `id` VARCHAR(40) NOT NULL,
    `solution_id` VARCHAR(40) NOT NULL,
    `parent_id` VARCHAR(40) NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `content` TEXT NOT NULL,
    `likes` INTEGER NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `solution_votes` (
    `solution_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `vote_type` INTEGER NOT NULL,

    PRIMARY KEY (`solution_id`, `user_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `problem_details` ADD CONSTRAINT `problem_details_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_tag_relations` ADD CONSTRAINT `problem_tag_relations_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_tag_relations` ADD CONSTRAINT `problem_tag_relations_tag_id_fkey` FOREIGN KEY (`tag_id`) REFERENCES `problem_tags`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_languages` ADD CONSTRAINT `problem_languages_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_examples` ADD CONSTRAINT `problem_examples_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_problems` ADD CONSTRAINT `contest_problems_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_problems` ADD CONSTRAINT `contest_problems_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_posts` ADD CONSTRAINT `forum_posts_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_posts` ADD CONSTRAINT `forum_posts_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `forum_users`(`username`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_comments` ADD CONSTRAINT `forum_comments_post_id_fkey` FOREIGN KEY (`post_id`) REFERENCES `forum_posts`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_comments` ADD CONSTRAINT `forum_comments_parent_id_fkey` FOREIGN KEY (`parent_id`) REFERENCES `forum_comments`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_comments` ADD CONSTRAINT `forum_comments_author_id_fkey` FOREIGN KEY (`author_id`) REFERENCES `forum_users`(`username`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_lists` ADD CONSTRAINT `problem_lists_group_id_fkey` FOREIGN KEY (`group_id`) REFERENCES `problem_list_groups`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `solutions` ADD CONSTRAINT `solutions_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `solutions` ADD CONSTRAINT `solutions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `solution_comments` ADD CONSTRAINT `solution_comments_solution_id_fkey` FOREIGN KEY (`solution_id`) REFERENCES `solutions`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `solution_comments` ADD CONSTRAINT `solution_comments_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `solution_comments` ADD CONSTRAINT `solution_comments_parent_id_fkey` FOREIGN KEY (`parent_id`) REFERENCES `solution_comments`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `solution_votes` ADD CONSTRAINT `solution_votes_solution_id_fkey` FOREIGN KEY (`solution_id`) REFERENCES `solutions`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `solution_votes` ADD CONSTRAINT `solution_votes_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
