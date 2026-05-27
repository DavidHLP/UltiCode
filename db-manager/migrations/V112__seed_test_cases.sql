-- UltiCode Migration: V112__seed_test_cases
-- Copies existing problem_examples into test_cases as sample cases,
-- and adds hidden cases for official judging.

INSERT INTO `test_cases` (`id`, `problem_id`, `is_sample`, `is_hidden`, `test_order`, `input_text`, `output_text`, `explanation`, `inputs`, `constraints`)
SELECT
  CONCAT('tc-', `id`),
  `problem_id`,
  1,
  0,
  `example_order`,
  `input_text`,
  `output_text`,
  `explanation`,
  `inputs`,
  NULL
FROM `problem_examples`;

-- Hidden test cases for Two Sum (problem_id = 1)
INSERT INTO `test_cases` (`id`, `problem_id`, `is_sample`, `is_hidden`, `test_order`, `input_text`, `output_text`, `explanation`, `inputs`, `constraints`) VALUES
('tc-two-sum-hidden-1', 1, 0, 1, 3, 'nums = [1,2,3,4,5], target = 8', '[2,4]', 'Hidden case: 3+5=8', '[{"name":"nums","value":"[1,2,3,4,5]"},{"name":"target","value":"8"}]', NULL),
('tc-two-sum-hidden-2', 1, 0, 1, 4, 'nums = [0,4,3,0], target = 0', '[0,3]', 'Hidden case: 0+0=0', '[{"name":"nums","value":"[0,4,3,0]"},{"name":"target","value":"0"}]', NULL);

-- Hidden test cases for Longest Substring (problem_id = 2)
INSERT INTO `test_cases` (`id`, `problem_id`, `is_sample`, `is_hidden`, `test_order`, `input_text`, `output_text`, `explanation`, `inputs`, `constraints`) VALUES
('tc-longest-sub-hidden-1', 2, 0, 1, 3, 's = ""', '0', 'Hidden case: empty string', '[{"name":"s","value":"\"\""}]', NULL);

-- Hidden test cases for Merge Intervals (problem_id = 3)
INSERT INTO `test_cases` (`id`, `problem_id`, `is_sample`, `is_hidden`, `test_order`, `input_text`, `output_text`, `explanation`, `inputs`, `constraints`) VALUES
('tc-merge-hidden-1', 3, 0, 1, 3, 'intervals = [[1,2],[3,5],[6,7],[8,10],[12,16]]', '[[1,2],[3,5],[6,7],[8,10],[12,16]]', 'Hidden case: no overlap', '[{"name":"intervals","value":"[[1,2],[3,5],[6,7],[8,10],[12,16]]"}]', NULL);
