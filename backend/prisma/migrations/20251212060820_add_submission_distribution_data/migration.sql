-- AlterTable
ALTER TABLE `submissions` ADD COLUMN `memoryDistBinsMb` JSON NULL,
    ADD COLUMN `runtimeDistBinsMs` JSON NULL;
