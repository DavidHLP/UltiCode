SET FOREIGN_KEY_CHECKS=0;

-- Fix corrupted banner_tag in problem_lists
-- The values were double-encoded due to missing useUnicode=true in JDBC URL
-- bannerTag "å¹¶å'" = "并发" (the correct Chinese text)

UPDATE `problem_lists` SET `banner_tag` = '并发' WHERE `id` = 'list-concurrency';

SET FOREIGN_KEY_CHECKS=1;