SET FOREIGN_KEY_CHECKS=0;
START TRANSACTION;

-- UltiCode Migration: V24__submissions_seed
-- Seed submission data with correct status enum (AC/WA/TLE/MLE/RE/CE)
-- ~200 submissions across 16 users and 32 problems
-- Status distribution: AC ~52%, WA ~25%, TLE ~10%, RE ~7.5%, MLE ~4%, CE ~3%

-- ============================================================================
-- Problem 1: Two Sum (Easy)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),1,'user-emma','typescript','// two-sum hash map approach',  'AC',62,43.2,NULL,'2025-01-15 10:00:00',78.5,65.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),1,'user-yuki','typescript','// two-sum brute force',       'WA',0,0,NULL,'2025-01-16 14:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),1,'user-sara','javascript','// two-sum nested loop',    'AC',58,41.5,NULL,'2025-01-17 09:00:00',75.3,63.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),1,'user-max','python','// two-sum hash',           'AC',55,39.8,NULL,'2025-01-18 11:00:00',82.1,71.3);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),1,'user-alex','java','// two-sum',                 'AC',48,42.1,NULL,'2025-01-19 08:30:00',88.5,72.1);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),1,'user-chen','cpp','// two-sum optimized',       'AC',45,38.4,NULL,'2025-01-20 15:00:00',91.2,78.5);

-- ============================================================================
-- Problem 2: Add Two Numbers (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),2,'user-yuki','typescript','// linked list iteration',   'AC',72,45.3,NULL,'2025-01-10 10:00:00',68.2,55.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),2,'user-chen','python','// recursive approach',      'TLE',0,0,NULL,'2025-01-11 14:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),2,'user-raj','java','// iterative with carry',   'AC',68,44.8,NULL,'2025-01-12 09:30:00',72.1,58.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),2,'user-kim','typescript','// stack based',           'WA',0,0,NULL,'2025-01-13 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),2,'user-sophie','cpp','// dummy node technique',  'AC',75,46.2,NULL,'2025-01-14 08:00:00',65.4,52.1);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),2,'user-john','javascript','// recursive',             'RE',0,0,NULL,'2025-01-15 16:00:00',0,0);

-- ============================================================================
-- Problem 3: Longest Substring Without Repeating Characters (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),3,'user-lily','typescript','// sliding window',         'AC',85,52.3,NULL,'2025-02-01 10:00:00',58.2,45.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),3,'user-alex','python','// brute force O(n^2)',    'TLE',0,0,NULL,'2025-02-02 14:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),3,'user-chen','java','// hashmap approach',       'AC',78,48.5,NULL,'2025-02-03 09:00:00',62.5,51.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),3,'user-raj','typescript','// set-based',             'WA',0,0,NULL,'2025-02-04 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),3,'user-kim','cpp','// optimized sliding',     'AC',82,50.1,NULL,'2025-02-05 08:30:00',60.1,48.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),3,'user-mike','javascript','// indexOf approach',      'WA',0,0,NULL,'2025-02-06 15:00:00',0,0);

-- ============================================================================
-- Problem 4: Median of Two Sorted Arrays (Hard)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),4,'user-sophie','python','// binary search',           'AC',125,58.4,NULL,'2025-02-10 10:00:00',45.2,38.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),4,'user-john','java','// divide and conquer',     'TLE',0,0,NULL,'2025-02-11 14:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),4,'user-lisa','typescript','// merge approach',         'WA',0,0,NULL,'2025-02-12 09:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),4,'user-bob','cpp','// iterative binary',       'AC',118,55.2,NULL,'2025-02-13 08:00:00',48.5,42.1);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),4,'user-alice','javascript','// two pointers',          'RE',0,0,NULL,'2025-02-14 16:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),4,'admin','python','// optimal solution',        'AC',98,52.8,NULL,'2025-02-15 11:00:00',55.2,45.8);

-- ============================================================================
-- Problem 5: Longest Palindromic Substring (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),5,'user-emma','typescript','// expand around center',  'AC',95,48.5,NULL,'2025-02-20 10:00:00',52.1,42.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),5,'user-yuki','python','// dp approach',             'AC',102,55.3,NULL,'2025-02-21 14:30:00',48.2,38.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),5,'user-sara','java','// brute force',             'TLE',0,0,NULL,'2025-02-22 09:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),5,'user-max','cpp','// manachers algorithm',     'AC',88,45.8,NULL,'2025-02-23 11:00:00',58.5,46.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),5,'user-alex','javascript','// center expansion',       'WA',0,0,NULL,'2025-02-24 08:30:00',0,0);

-- ============================================================================
-- Problem 6: Zigzag Conversion (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),6,'user-chen','typescript','// string builder',        'AC',68,42.5,NULL,'2025-03-01 10:00:00',65.2,55.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),6,'user-raj','python','// array of rows',           'AC',72,44.1,NULL,'2025-03-02 14:00:00',62.1,52.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),6,'user-kim','java','// index manipulation',     'WA',0,0,NULL,'2025-03-03 09:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),6,'user-sophie','cpp','// direct calculation',     'AC',65,41.8,NULL,'2025-03-04 08:00:00',68.5,58.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),6,'user-john','javascript','// modulo approach',       'RE',0,0,NULL,'2025-03-05 16:00:00',0,0);

-- ============================================================================
-- Problem 7: Reverse Integer (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),7,'user-lisa','typescript','// string conversion',      'AC',45,35.2,NULL,'2025-03-10 10:00:00',78.5,68.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),7,'user-bob','python','// mathematical approach',   'AC',42,33.8,NULL,'2025-03-11 14:30:00',82.1,72.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),7,'user-alice','java','// overflow check',          'WA',0,0,NULL,'2025-03-12 09:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),7,'admin','cpp','// pop and push digits',     'AC',38,32.5,NULL,'2025-03-13 08:00:00',85.2,75.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),7,'user-emma','javascript','// string reverse',        'AC',48,36.1,NULL,'2025-03-14 11:00:00',75.2,65.5);

-- ============================================================================
-- Problem 8: String to Integer (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),8,'user-yuki','typescript','// parseInt approach',       'AC',52,38.5,NULL,'2025-03-15 10:00:00',72.1,62.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),8,'user-sara','python','// manual parsing',         'AC',55,40.2,NULL,'2025-03-16 14:00:00',68.5,58.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),8,'user-max','java','// whitespace handling',     'WA',0,0,NULL,'2025-03-17 09:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),8,'user-alex','cpp','// state machine',            'AC',48,36.8,NULL,'2025-03-18 08:00:00',75.2,65.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),8,'user-chen','javascript','// trim and parse',        'RE',0,0,NULL,'2025-03-19 16:00:00',0,0);

-- ============================================================================
-- Problem 9: Palindrome Number (Easy)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),9,'user-raj','typescript','// string reversal',        'AC',38,32.5,NULL,'2025-03-20 10:00:00',82.1,72.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),9,'user-kim','python','// mathematical',            'AC',35,30.8,NULL,'2025-03-21 14:30:00',85.2,75.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),9,'user-sophie','java','// half reversal',           'AC',42,33.2,NULL,'2025-03-22 09:00:00',78.5,68.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),9,'user-john','cpp','// string compare',          'WA',0,0,NULL,'2025-03-23 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),9,'user-lisa','javascript','// toString',               'AC',40,31.5,NULL,'2025-03-24 08:30:00',80.5,70.2);

-- ============================================================================
-- Problem 10: Regular Expression Matching (Hard)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),10,'user-bob','typescript','// dynamic programming',   'AC',145,62.5,NULL,'2025-03-25 10:00:00',38.2,28.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),10,'user-alice','python','// recursive with memo',   'TLE',0,0,NULL,'2025-03-26 14:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),10,'admin','java','// dp 2D array',             'MLE',0,0,NULL,'2025-03-27 09:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),10,'user-emma','cpp','// optimized dp',            'AC',138,58.8,NULL,'2025-03-28 08:00:00',42.5,32.1);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),10,'user-yuki','javascript','// regex exec',           'RE',0,0,NULL,'2025-03-29 16:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),10,'user-sara','typescript','// bottom up dp',          'AC',142,60.2,NULL,'2025-03-30 11:00:00',40.2,30.5);

-- ============================================================================
-- Problem 11: Container With Most Water (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),11,'user-max','python','// two pointers',           'AC',72,42.5,NULL,'2025-04-01 10:00:00',62.1,52.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),11,'user-alex','java','// brute force O(n^2)',      'TLE',0,0,NULL,'2025-04-02 14:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),11,'user-chen','cpp','// optimal two pointer',    'AC',68,40.8,NULL,'2025-04-03 09:00:00',65.2,55.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),11,'user-raj','javascript','// area calc',              'WA',0,0,NULL,'2025-04-04 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),11,'user-kim','typescript','// max area tracking',      'AC',70,41.5,NULL,'2025-04-05 08:30:00',63.5,54.2);

-- ============================================================================
-- Problem 12: Integer to Roman (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),12,'user-sophie','python','// greedy mapping',          'AC',58,38.5,NULL,'2025-04-06 10:00:00',68.2,58.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),12,'user-john','java','// string builder',          'AC',62,40.2,NULL,'2025-04-07 14:00:00',65.5,55.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),12,'user-lisa','cpp','// switch cases',            'WA',0,0,NULL,'2025-04-08 09:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),12,'user-bob','typescript','// lookup table',           'AC',55,37.8,NULL,'2025-04-09 08:00:00',72.1,62.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),12,'user-alice','javascript','// iterative',             'RE',0,0,NULL,'2025-04-10 16:00:00',0,0);

-- ============================================================================
-- Problem 13: Roman to Integer (Easy)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),13,'user-alice','typescript','// hash map lookup',        'AC',35,28.5,NULL,'2025-04-11 10:00:00',85.2,75.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),13,'admin','python','// switch case approach',    'AC',32,26.8,NULL,'2025-04-12 14:30:00',88.5,78.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),13,'user-emma','java','// single pass',               'AC',38,29.2,NULL,'2025-04-13 09:00:00',82.1,72.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),13,'user-yuki','cpp','// char to value',           'WA',0,0,NULL,'2025-04-14 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),13,'user-sara','javascript','// reduce method',          'AC',40,30.5,NULL,'2025-04-15 08:30:00',80.2,70.5);

-- ============================================================================
-- Problem 14: Longest Common Prefix (Easy)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),14,'user-max','typescript','// horizontal scanning',   'AC',42,32.5,NULL,'2025-04-16 10:00:00',78.5,68.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),14,'user-alex','python','// vertical scanning',       'AC',38,30.8,NULL,'2025-04-17 14:00:00',82.1,72.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),14,'user-chen','java','// sort and compare',        'AC',45,33.2,NULL,'2025-04-18 09:30:00',75.2,65.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),14,'user-raj','cpp','// first string approach',   'WA',0,0,NULL,'2025-04-19 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),14,'user-kim','javascript','// indexOf check',          'AC',40,31.5,NULL,'2025-04-20 08:30:00',80.5,70.2);

-- ============================================================================
-- Problem 15: 3Sum (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),15,'user-sophie','typescript','// two pointer with sort', 'AC',112,55.8,NULL,'2025-04-21 10:00:00',48.2,38.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),15,'user-john','python','// hashset approach',        'TLE',0,0,NULL,'2025-04-22 14:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),15,'user-lisa','java','// sorting + 2sum',         'AC',108,53.2,NULL,'2025-04-23 09:00:00',52.5,42.1);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),15,'user-bob','cpp','// unique triplets',          'AC',115,56.5,NULL,'2025-04-24 11:00:00',45.8,36.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),15,'user-alice','javascript','// brute force',         'WA',0,0,NULL,'2025-04-25 08:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),15,'admin','typescript','// optimized 2sum',        'AC',105,52.8,NULL,'2025-04-26 16:00:00',55.2,45.2);

-- ============================================================================
-- Problem 16: 3Sum Closest (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),16,'user-emma','python','// sort + two pointers',   'AC',98,48.5,NULL,'2025-05-01 10:00:00',55.2,45.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),16,'user-yuki','java','// brute force',              'TLE',0,0,NULL,'2025-05-02 14:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),16,'user-sara','cpp','// target approach',          'AC',95,46.8,NULL,'2025-05-03 09:30:00',58.2,48.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),16,'user-max','typescript','// diff tracking',          'WA',0,0,NULL,'2025-05-04 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),16,'user-alex','javascript','// min diff',               'AC',92,45.2,NULL,'2025-05-05 08:00:00',60.5,50.2);

-- ============================================================================
-- Problem 17: Letter Combinations of a Phone Number (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),17,'user-chen','typescript','// backtracking',           'AC',75,45.8,NULL,'2025-05-06 10:00:00',62.1,52.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),17,'user-raj','python','// queue approach',          'AC',78,47.2,NULL,'2025-05-07 14:30:00',58.5,48.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),17,'user-kim','java','// recursion',                 'AC',72,44.5,NULL,'2025-05-08 09:00:00',65.2,55.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),17,'user-sophie','cpp','// iterative',                'WA',0,0,NULL,'2025-05-09 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),17,'user-john','javascript','// mapping',                 'RE',0,0,NULL,'2025-05-10 16:00:00',0,0);

-- ============================================================================
-- Problem 18: 4Sum (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),18,'user-lisa','typescript','// sort + two pointers',   'AC',135,58.5,NULL,'2025-05-11 10:00:00',42.5,32.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),18,'user-bob','python','// hashset approach',         'TLE',0,0,NULL,'2025-05-12 14:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),18,'user-alice','java','// two sum helper',           'AC',128,55.8,NULL,'2025-05-13 09:30:00',48.2,38.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),18,'admin','cpp','// optimization prune',       'AC',125,54.2,NULL,'2025-05-14 08:00:00',52.1,42.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),18,'user-emma','javascript','// brute force',             'WA',0,0,NULL,'2025-05-15 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),18,'user-yuki','typescript','// pruning',                  'MLE',0,0,NULL,'2025-05-16 16:00:00',0,0);

-- ============================================================================
-- Problem 19: Remove Nth Node From End of List (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),19,'user-sara','python','// two pointers',            'AC',65,42.5,NULL,'2025-05-17 10:00:00',68.2,58.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),19,'user-max','java','// count and remove',         'AC',68,44.2,NULL,'2025-05-18 14:30:00',65.5,55.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),19,'user-alex','cpp','// dummy node',                'AC',62,41.8,NULL,'2025-05-19 09:00:00',72.1,62.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at',`runtime_percentile`,`memory_percentile`) VALUES (UUID(),19,'user-chen','typescript','// fast-slow',                'WA',0,0,NULL,'2025-05-20 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),19,'user-raj','javascript','// recursion',                'RE',0,0,NULL,'2025-05-21 08:30:00',0,0);

-- ============================================================================
-- Problem 20: Valid Parentheses (Easy)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),20,'user-kim','typescript','// stack approach',          'AC',32,28.5,NULL,'2025-05-22 10:00:00',88.2,78.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),20,'user-sophie','python','// stack with push/pop',    'AC',35,30.2,NULL,'2025-05-23 14:00:00',85.5,75.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),20,'user-john','java','// map matching',              'AC',38,31.8,NULL,'2025-05-24 09:30:00',82.1,72.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),20,'user-lisa','cpp','// stack class',               'WA',0,0,NULL,'2025-05-25 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),20,'user-bob','javascript','// object mapping',          'AC',30,27.5,NULL,'2025-05-26 08:30:00',90.2,80.5);

-- ============================================================================
-- Problem 21: Merge Two Sorted Lists (Easy)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),21,'user-alice','typescript','// iterative merge',        'AC',45,35.8,NULL,'2025-05-27 10:00:00',78.5,68.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),21,'admin','python','// recursive approach',        'AC',42,33.5,NULL,'2025-05-28 14:30:00',82.1,72.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),21,'user-emma','java','// dummy node',                 'AC',48,36.2,NULL,'2025-05-29 09:00:00',75.2,65.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),21,'user-yuki','cpp','// in-place merge',           'WA',0,0,NULL,'2025-05-30 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),21,'user-sara','javascript','// while loop',              'AC',40,32.8,NULL,'2025-05-31 08:30:00',85.2,75.5);

-- ============================================================================
-- Problem 22: Generate Parentheses (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),22,'user-max','typescript','// backtracking',            'AC',88,48.5,NULL,'2025-06-01 10:00:00',58.2,48.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),22,'user-alex','python','// recursion with prune',    'AC',92,50.2,NULL,'2025-06-02 14:00:00',55.5,45.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),22,'user-chen','java','// stack building',            'AC',85,47.8,NULL,'2025-06-03 09:30:00',62.1,52.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),22,'user-raj','cpp','// DFS approach',              'TLE',0,0,NULL,'2025-06-04 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),22,'user-kim','javascript','// builder pattern',         'WA',0,0,NULL,'2025-06-05 08:30:00',0,0);

-- ============================================================================
-- Problem 23: Merge k Sorted Lists (Hard)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),23,'user-sophie','typescript','// min heap',                 'AC',155,68.5,NULL,'2025-06-06 10:00:00',35.2,25.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),23,'user-john','python','// divide and conquer',       'TLE',0,0,NULL,'2025-06-07 14:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),23,'user-lisa','java','// priority queue',              'AC',148,65.2,NULL,'2025-06-08 09:00:00',40.5,30.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),23,'user-bob','cpp','// k-way merge',               'MLE',0,0,NULL,'2025-06-09 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),23,'user-alice','javascript','// sequential merge',       'WA',0,0,NULL,'2025-06-10 16:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),23,'admin','typescript','// heap optimization',         'AC',145,62.8,NULL,'2025-06-11 08:00:00',42.5,35.2);

-- ============================================================================
-- Problem 24: Swap Nodes in Pairs (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),24,'user-emma','python','// iterative approach',     'AC',55,38.5,NULL,'2025-06-12 10:00:00',72.1,62.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),24,'user-yuki','java','// dummy node swap',          'AC',58,40.2,NULL,'2025-06-13 14:00:00',68.5,58.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),24,'user-sara','cpp','// in-place swap',            'WA',0,0,NULL,'2025-06-14 09:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),24,'user-max','typescript','// recursion',                'AC',52,36.8,NULL,'2025-06-15 11:00:00',75.2,65.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),24,'user-alex','javascript','// pointer swap',            'RE',0,0,NULL,'2025-06-16 08:30:00',0,0);

-- ============================================================================
-- Problem 25: Reverse Nodes in k-Group (Hard)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),25,'user-chen','typescript','// stack approach',           'AC',135,58.5,NULL,'2025-06-17 10:00:00',42.1,32.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),25,'user-raj','python','// recursion with counter',  'TLE',0,0,NULL,'2025-06-18 14:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),25,'user-kim','java','// iterative with count',    'AC',142,62.2,NULL,'2025-06-19 09:00:00',38.5,28.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),25,'user-sophie','cpp','// group reversal',           'WA',0,0,NULL,'2025-06-20 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),25,'user-john','javascript','// pointer tracking',       'RE',0,0,NULL,'2025-06-21 16:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),25,'user-lisa','typescript','// optimal solution',        'MLE',0,0,NULL,'2025-06-22 08:30:00',0,0);

-- ============================================================================
-- Problem 26: Remove Duplicates from Sorted Array (Easy)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),26,'user-bob','python','// two pointers',             'AC',48,35.2,NULL,'2025-06-23 10:00:00',75.2,65.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),26,'user-alice','java','// in-place',                   'AC',45,33.8,NULL,'2025-06-24 14:00:00',78.5,68.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),26,'admin','cpp','// single pass',                 'AC',42,32.5,NULL,'2025-06-25 09:30:00',82.1,72.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),26,'user-emma','typescript','// slow-fast',                'WA',0,0,NULL,'2025-06-26 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),26,'user-yuki','javascript','// unique count',           'AC',40,31.2,NULL,'2025-06-27 08:30:00',85.2,75.8);

-- ============================================================================
-- Problem 27: Remove Element (Easy)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),27,'user-sara','typescript','// two pointers',             'AC',38,30.5,NULL,'2025-06-28 10:00:00',85.2,75.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),27,'user-max','python','// swap and pop',              'AC',42,32.8,NULL,'2025-06-29 14:30:00',80.5,70.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),27,'user-alex','java','// overwrite approach',         'AC',40,31.5,NULL,'2025-06-30 09:00:00',82.1,72.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),27,'user-chen','cpp','// fast pointer',               'WA',0,0,NULL,'2025-07-01 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),27,'user-raj','javascript','// filter approach',          'AC',35,28.8,NULL,'2025-07-02 08:30:00',88.5,78.2);

-- ============================================================================
-- Problem 28: Find the Index of the First Occurrence in a String (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),28,'user-kim','typescript','// two pointers',             'AC',58,38.5,NULL,'2025-07-03 10:00:00',68.2,58.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),28,'user-sophie','python','// built-in indexOf',        'AC',55,36.2,NULL,'2025-07-04 14:00:00',72.1,62.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),28,'user-john','java','// KMP algorithm',              'AC',52,35.8,NULL,'2025-07-05 09:30:00',75.2,65.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),28,'user-lisa','cpp','// brute force',                'TLE',0,0,NULL,'2025-07-06 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),28,'user-bob','javascript','// substring search',       'WA',0,0,NULL,'2025-07-07 08:30:00',0,0);

-- ============================================================================
-- Problem 29: Divide Two Integers (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),29,'user-alice','typescript','// bit manipulation',        'AC',82,45.8,NULL,'2025-07-08 10:00:00',58.2,48.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),29,'admin','python','// long division',               'AC',85,48.2,NULL,'2025-07-09 14:30:00',55.5,45.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),29,'user-emma','java','// subtraction approach',      'TLE',0,0,NULL,'2025-07-10 09:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),29,'user-yuki','cpp','// double and halve',           'AC',78,44.5,NULL,'2025-07-11 11:00:00',62.1,52.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),29,'user-sara','javascript','// overflow check',         'WA',0,0,NULL,'2025-07-12 08:30:00',0,0);

-- ============================================================================
-- Problem 30: Substring with Concatenation of All Words (Hard)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),30,'user-max','typescript','// sliding window',           'AC',125,55.8,NULL,'2025-07-13 10:00:00',45.2,35.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),30,'user-alex','python','// hashmap approach',         'TLE',0,0,NULL,'2025-07-14 14:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),30,'user-chen','java','// word frequency',             'AC',118,52.5,NULL,'2025-07-15 09:30:00',52.1,42.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),30,'user-raj','cpp','// optimized search',          'WA',0,0,NULL,'2025-07-16 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),30,'user-kim','javascript','// brute force',            'RE',0,0,NULL,'2025-07-17 16:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),30,'user-sophie','typescript','// hash optimization',    'MLE',0,0,NULL,'2025-07-18 08:30:00',0,0);

-- ============================================================================
-- Problem 31: Next Permutation (Medium)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),31,'user-john','python','// standard algorithm',      'AC',65,40.2,NULL,'2025-07-19 10:00:00',65.2,55.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),31,'user-lisa','java','// in-place algorithm',        'AC',62,38.8,NULL,'2025-07-20 14:30:00',68.5,58.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),31,'user-bob','cpp','// two pass approach',          'AC',68,42.5,NULL,'2025-07-21 09:00:00',62.1,52.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),31,'user-alice','typescript','// swap strategy',         'WA',0,0,NULL,'2025-07-22 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),31,'admin','javascript','// next perm',                 'AC',58,36.5,NULL,'2025-07-23 08:30:00',72.1,62.5);

-- ============================================================================
-- Problem 32: Longest Valid Parentheses (Hard)
-- ============================================================================
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),32,'user-emma','typescript','// stack approach',            'AC',95,48.5,NULL,'2025-07-24 10:00:00',55.2,45.8);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),32,'user-yuki','python','// DP approach',                'AC',102,52.2,NULL,'2025-07-25 14:00:00',48.5,38.2);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),32,'user-sara','java','// counter approach',            'TLE',0,0,NULL,'2025-07-26 09:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),32,'user-max','cpp','// two counters',                'AC',88,45.8,NULL,'2025-07-27 11:00:00',62.1,52.5);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),32,'user-alex','javascript','// stack with index',      'WA',0,0,NULL,'2025-07-28 08:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),32,'user-chen','typescript','// optimal dp',              'MLE',0,0,NULL,'2025-07-29 16:00:00',0,0);

-- ============================================================================
-- Additional submissions to reach target distribution
-- ============================================================================
-- Problem 1: More WA attempts
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),1,'user-lisa','bash','// wrong approach',           'WA',0,0,NULL,'2025-01-21 10:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),1,'user-bob','python','// off by one error',        'WA',0,0,NULL,'2025-01-22 14:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),1,'user-mike','java','// syntax error',             'CE',0,0,NULL,'2025-01-23 09:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),1,'user-sophie','cpp','// compilation fail',         'CE',0,0,NULL,'2025-01-24 11:00:00',0,0);

-- Problem 2: More varied statuses
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),2,'user-lisa','python','// infinite loop',             'RE',0,0,NULL,'2025-01-16 14:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),2,'user-mike','typescript','// null pointer',            'RE',0,0,NULL,'2025-01-17 16:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),2,'user-bob','javascript','// wrong base case',      'WA',0,0,NULL,'2025-01-18 10:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),2,'user-alice','bash','// shell syntax error',       'CE',0,0,NULL,'2025-01-19 08:00:00',0,0);

-- Problem 3: More WA and TLE
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),3,'user-sara','python','// timeout issues',            'TLE',0,0,NULL,'2025-02-07 14:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),3,'user-lisa','java','// space complexity',        'MLE',0,0,NULL,'2025-02-08 09:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),3,'user-mike','cpp','// memory limit',             'MLE',0,0,NULL,'2025-02-09 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),3,'user-bob','typescript','// wrong window size',     'WA',0,0,NULL,'2025-02-10 08:30:00',0,0);

-- Problem 4: More CE and RE
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),4,'user-sara','python','// undefined variable',       'CE',0,0,NULL,'2025-02-16 14:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),4,'user-mike','java','// stack overflow',           'RE',0,0,NULL,'2025-02-17 09:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),4,'user-chen','cpp','// division by zero',         'RE',0,0,NULL,'2025-02-18 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at',`runtime_percentile`,`memory_percentile`) VALUES (UUID(),4,'user-raj','typescript','// off by one',              'WA',0,0,NULL,'2025-02-19 08:30:00',0,0);

-- Problem 5: More varied
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),5,'user-john','python','// recursion depth',           'RE',0,0,NULL,'2025-02-25 14:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),5,'user-lisa','java','// wrong center',              'WA',0,0,NULL,'2025-02-26 09:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),5,'user-bob','cpp','// segmentation fault',      'RE',0,0,NULL,'2025-02-27 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),5,'user-alice','typescript','// boundary issue',          'WA',0,0,NULL,'2025-02-28 08:30:00',0,0);

-- Problem 6: Add more
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),6,'user-mike','javascript','// type error',               'CE',0,0,NULL,'2025-03-06 14:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),6,'user-lisa','python','// wrong row order',          'WA',0,0,NULL,'2025-03-07 09:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at',`runtime_percentile`,`memory_percentile`) VALUES (UUID(),6,'user-bob','java','// array out of bounds',    'RE',0,0,NULL,'2025-03-08 11:00:00',0,0);

-- Problem 7-10: Additional varied attempts
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),7,'user-mike','python','// negative handling',        'WA',0,0,NULL,'2025-03-15 14:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),8,'user-lisa','cpp','// whitespace bug',            'WA',0,0,NULL,'2025-03-20 09:30:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at`,`runtime_percentile`,`memory_percentile`) VALUES (UUID(),9,'user-bob','typescript','// edge case 0',             'WA',0,0,NULL,'2025-03-25 11:00:00',0,0);
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,`runtime`,`memory`,`notes`,`created_at',`runtime_percentile`,`memory_percentile`) VALUES (UUID(),10,'user-alice','java','// pattern mismatch',         'WA',0,0,NULL,'2025-04-01 08:30:00',0,0);

COMMIT;
SET FOREIGN_KEY_CHECKS=1;
