-- Align admin user ID across comment and mirror tables
UPDATE `forum_comments`
SET `author_id` = (SELECT `id` FROM `users` WHERE `username` = 'admin' AND `role` = 'ADMIN' LIMIT 1)
WHERE `author_id` = 'admin';

UPDATE `solution_comments`
SET `user_id` = (SELECT `id` FROM `users` WHERE `username` = 'admin' AND `role` = 'ADMIN' LIMIT 1)
WHERE `user_id` = 'admin';

UPDATE `forum_users`
SET `id` = (SELECT `id` FROM `users` WHERE `username` = 'admin' AND `role` = 'ADMIN' LIMIT 1)
WHERE `username` = 'admin';
