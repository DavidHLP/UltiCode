ALTER TABLE `problem_lists`
  ADD COLUMN `banner_tag` VARCHAR(30) NULL,
  ADD COLUMN `banner_icon` VARCHAR(50) NULL,
  ADD COLUMN `banner_theme` VARCHAR(30) NULL,
  ADD COLUMN `banner_order` INT NOT NULL DEFAULT 0;
