const now = '2024-11-01T00:00:00.000Z';

const data = {
  problem_details: [
    {
      id: 'pd-two-sum',
      problem_id: 1,
      slug: 'two-sum',
      summary:
        'Given an array of integers `nums` and an integer `target`, return _indices of the two numbers such that they add up to `target`_.\n\nYou may assume that each input would have **exactly one solution**, and you may not use the *same* element twice.\n\nYou can return the answer in any order.',
      companies: ['Amazon', 'Google', 'Apple', 'Adobe', 'Microsoft', 'Bloomberg'],
      interactions: {
        counts: { likes: 54300, dislikes: 1800, favorites: 22000 },
        viewer: { reaction: 'like', isFavorite: true },
      },
      difficulty_rating: 1050,
      updated_at: now,
      follow_up: 'Can you come up with an algorithm that is less than $O(n^2)$ time complexity?',
      constraints_json: [
        '$2 \\leq nums.length \\leq 10^4$',
        '$-10^9 \\leq nums[i] \\leq 10^9$',
        '$-10^9 \\leq target \\leq 10^9$',
        '**Only one valid answer exists.**',
      ],
    },
  ],
  problem_examples: [
    {
      id: 'ex-two-sum-1',
      problem_id: 1,
      example_order: 0,
      input_text: 'nums = [2,7,11,15], target = 9',
      output_text: '[0,1]',
      explanation: 'Because nums[0] + nums[1] == 9, we return [0, 1].',
    },
    {
      id: 'ex-two-sum-2',
      problem_id: 1,
      example_order: 1,
      input_text: 'nums = [3,2,4], target = 6',
      output_text: '[1,2]',
      explanation: 'nums[1] + nums[2] == 6, so we return [1, 2].',
    },
    {
      id: 'ex-two-sum-3',
      problem_id: 1,
      example_order: 2,
      input_text: 'nums = [3,3], target = 6',
      output_text: '[0,1]',
      explanation: 'The same element cannot be used twice, but two different elements with value 3 can be used.',
    },
  ],
  problem_approaches: [
    {
      id: 'ap-two-sum-hashmap',
      problem_id: 1,
      title: 'One-pass Hash Table',
      summary:
        'It turns out we can do it in one-pass. While we iterate and inserting elements into the table, we also look back to check if current element\'s complement already exists in the table. If it exists, we have found a solution and return immediately.',
      time_complexity: 'O(n)',
      space_complexity: 'O(n)',
      code_snippet:
        'function twoSum(nums: number[], target: number) {\n  const map = new Map();\n  for (let i = 0; i < nums.length; i++) {\n    const complement = target - nums[i];\n    if (map.has(complement)) {\n      return [map.get(complement), i];\n    }\n    map.set(nums[i], i);\n  }\n  return [];\n}',
      language: 'TypeScript',
    },
  ],
  problem_approach_steps: [],
  problem_languages: [
    {
      id: 'lang-ts',
      problem_id: 1,
      label: 'TypeScript',
      value: 'typescript',
      starterCode: 'function twoSum(nums: number[], target: number): number[] {\n    \n};',
    },
    {
      id: 'lang-js',
      problem_id: 1,
      label: 'JavaScript',
      value: 'javascript',
      starterCode: '/**\n * @param {number[]} nums\n * @param {number} target\n * @return {number[]}\n */\nvar twoSum = function(nums, target) {\n    \n};',
    },
    {
      id: 'lang-py',
      problem_id: 1,
      label: 'Python',
      value: 'python',
      starterCode: 'class Solution:\n    def twoSum(self, nums: List[int], target: int) -> List[int]:\n        ',
    },
    {
      id: 'lang-java',
      problem_id: 1,
      label: 'Java',
      value: 'java',
      starterCode: 'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        \n    }\n}',
    },
    {
      id: 'lang-cpp',
      problem_id: 1,
      label: 'C++',
      value: 'cpp',
      starterCode: 'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        \n    }\n};',
    },
  ],
} as const;

export default data;
