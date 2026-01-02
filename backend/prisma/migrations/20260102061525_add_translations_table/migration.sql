-- CreateTable
CREATE TABLE `translations` (
    `id` VARCHAR(40) NOT NULL,
    `entity_type` VARCHAR(50) NOT NULL,
    `entity_id` VARCHAR(50) NOT NULL,
    `field_name` VARCHAR(50) NOT NULL,
    `locale` VARCHAR(10) NOT NULL,
    `content` TEXT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    INDEX `translations_entity_type_entity_id_locale_idx`(`entity_type`, `entity_id`, `locale`),
    INDEX `translations_locale_idx`(`locale`),
    UNIQUE INDEX `translations_entity_type_entity_id_field_name_locale_key`(`entity_type`, `entity_id`, `field_name`, `locale`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
