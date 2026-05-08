SET FOREIGN_KEY_CHECKS=0;

-- Fix users table corrupted names
UPDATE `users` SET `name` = '系统管理员' WHERE `id` = 'u-mod-001';

-- Fix moderation_queue corrupted resolution notes
UPDATE `moderation_queue` SET `resolution_note` = '关于比赛心态的建设性讨论。未发现违规。' WHERE `id` = 'mq-010';

SET FOREIGN_KEY_CHECKS=1;