-- CreateTable
CREATE TABLE `global_rankings` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `username` VARCHAR(120) NOT NULL,
    `global_rank` INTEGER NOT NULL,
    `rating` INTEGER NOT NULL DEFAULT 1500,
    `max_rating` INTEGER NOT NULL DEFAULT 1500,
    `contests_attended` INTEGER NOT NULL DEFAULT 0,
    `avatar` VARCHAR(255) NULL,
    `country` VARCHAR(10) NOT NULL DEFAULT 'CN',
    `badge` VARCHAR(50) NULL,

    UNIQUE INDEX `global_rankings_user_id_key`(`user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
