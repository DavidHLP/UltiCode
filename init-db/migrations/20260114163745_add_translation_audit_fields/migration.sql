-- AlterTable
ALTER TABLE `translations` ADD COLUMN `created_by` VARCHAR(40) NULL,
    ADD COLUMN `updated_by` VARCHAR(40) NULL;

-- CreateIndex
CREATE INDEX `translations_created_by_idx` ON `translations`(`created_by`);

-- CreateIndex
CREATE INDEX `translations_updated_by_idx` ON `translations`(`updated_by`);

-- AddForeignKey
ALTER TABLE `translations` ADD CONSTRAINT `translations_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `translations` ADD CONSTRAINT `translations_updated_by_fkey` FOREIGN KEY (`updated_by`) REFERENCES `users`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
