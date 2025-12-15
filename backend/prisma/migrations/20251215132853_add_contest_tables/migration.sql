-- CreateTable
CREATE TABLE `contest_rankings` (
    `id` VARCHAR(40) NOT NULL,
    `contest_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `username` VARCHAR(120) NOT NULL,
    `rank` INTEGER NOT NULL,
    `score` INTEGER NOT NULL,
    `finish_time_seconds` INTEGER NOT NULL,
    `q1_time_seconds` INTEGER NULL,
    `q2_time_seconds` INTEGER NULL,
    `q3_time_seconds` INTEGER NULL,
    `q4_time_seconds` INTEGER NULL,
    `rating_before` INTEGER NOT NULL,
    `rating_after` INTEGER NOT NULL,
    `rating_change` INTEGER NOT NULL,
    `country` VARCHAR(10) NOT NULL DEFAULT 'CN',

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `contest_participants` (
    `id` VARCHAR(40) NOT NULL,
    `contest_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `username` VARCHAR(120) NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `registered_at` DATETIME(3) NOT NULL,
    `started_at` DATETIME(3) NULL,
    `finished_at` DATETIME(3) NULL,
    `rank` INTEGER NULL,
    `score` INTEGER NOT NULL DEFAULT 0,
    `finish_time_seconds` INTEGER NULL,
    `is_virtual` BOOLEAN NOT NULL DEFAULT false,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `contest_rankings` ADD CONSTRAINT `contest_rankings_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_rankings` ADD CONSTRAINT `contest_rankings_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_participants` ADD CONSTRAINT `contest_participants_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_participants` ADD CONSTRAINT `contest_participants_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
