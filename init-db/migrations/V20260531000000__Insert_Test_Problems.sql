-- Insert test problems for management UI testing
-- Routes: /problems/1, /problems/1/code, /problems/1/cases, /problems/1/audit

-- Problem 1: Two Sum (Easy)
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `is_published`, `published_at`, `published_by`, `created_at`, `updated_at`, `version`) VALUES
(1, 'two-sum', 'Two Sum', 'Easy', 49.20, 'solved', 0, 1, 1, NOW(), 'u-admin-001', NOW(), NOW(), 1)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

-- Problem Details for problem 1
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `content`, `difficulty_rating`, `constraints_json`, `hints`, `updated_at`) VALUES
('pd-1', 1, 'two-sum', 'Find two numbers in an array that add up to a target.', '<p>Given an array of integers and a target, return indices of the two numbers such that they add up to target.</p>', 1300.0, '["2 <= nums.length <= 10^4", "-10^9 <= nums[i] <= 10^9", "-10^9 <= target <= 10^9"]', '["Try different approaches: brute force vs hash map"]', NOW())
ON DUPLICATE KEY UPDATE `summary` = VALUES(`summary`);

-- Examples for problem 1
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`) VALUES
('pe-1-1', 1, 1, '[2,7,11,15]\n9', '[0,1]', 'Because nums[0] + nums[1] == 9, we return [0, 1].'),
('pe-1-2', 1, 2, '[3,2,4]\n6', '[1,2]', NULL),
('pe-1-3', 1, 3, '[3,3]\n6', '[0,1]', NULL)
ON DUPLICATE KEY UPDATE `input_text` = VALUES(`input_text`);

-- Languages for problem 1
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES
('pl-1-python', 1, 'Python3', 'python3', 'python', 'def two_sum(nums, target):\n    pass'),
('pl-1-java', 1, 'Java', 'java', 'java', 'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        // Your code here\n    }\n}'),
('pl-1-cpp', 1, 'C++', 'cpp17', 'cpp', 'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        // Your code here\n    }\n};')
ON DUPLICATE KEY UPDATE `starter_code` = VALUES(`starter_code`);

-- Tags for problem 1
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`) VALUES
('tag-array', 'Array', 'array', '#3182CE', 'Array related problems', 0, NOW()),
('tag-hash', 'Hash Table', 'hash-table', '#805AD5', 'Hash table based solutions', 0, NOW())
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`);

INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES
(1, 'tag-array'),
(1, 'tag-hash')
ON DUPLICATE KEY UPDATE `tag_id` = VALUES(`tag_id`);

-- Version for problem 1
INSERT INTO `problem_versions` (`id`, `problem_id`, `version_number`, `snapshot_json`, `change_type`, `change_summary`, `created_by`, `created_at`) VALUES
(1, 1, 1, '{"title":"Two Sum","slug":"two-sum","difficulty":"Easy","isPublished":true}', 'CREATE', 'Initial version', 'u-admin-001', NOW())
ON DUPLICATE KEY UPDATE `version_number` = VALUES(`version_number`);