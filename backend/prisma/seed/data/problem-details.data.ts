import { PROBLEM_IDS } from './problems.data';

const now = '2024-11-01T00:00:00.000Z';

const data = {
  problem_details: [
    {
      id: 'pd-two-sum',
      problem_id: PROBLEM_IDS.TWO_SUM,
      slug: 'two-sum',
      summary:
        'Given an array of integers `nums` and an integer `target`, return _indices of the two numbers such that they add up to `target`_.\n\nYou may assume that each input would have **exactly one solution**, and you may not use the *same* element twice.\n\nYou can return the answer in any order.',
      companies: [
        'Amazon',
        'Google',
        'Apple',
        'Adobe',
        'Microsoft',
        'Bloomberg',
      ],
      interactions: {
        counts: { likes: 54300, dislikes: 1800, favorites: 22000 },
        viewer: { reaction: 'like', isFavorite: true },
      },
      difficulty_rating: 1050,
      updated_at: now,
      follow_up:
        'Can you come up with an algorithm that is less than $O(n^2)$ time complexity?',
      constraints_json: [
        '$2 \\leq nums.length \\leq 10^4$',
        '$-10^9 \\leq nums[i] \\leq 10^9$',
        '$-10^9 \\leq target \\leq 10^9$',
        '**Only one valid answer exists.**',
      ],
    },
    {
      id: 'pd-longest-substring',
      problem_id: PROBLEM_IDS.LONGEST_SUBSTRING,
      slug: 'longest-substring-without-repeating-characters',
      summary:
        'Given a string s, find the length of the longest substring without repeating characters.',
      companies: ['Google', 'Meta', 'Amazon'],
      difficulty_rating: 1420,
      updated_at: now,
      follow_up: 'Can you return the substring itself while keeping O(n) time?',
      constraints_json: [
        '$0 \\leq s.length \\leq 5 \\times 10^4$',
        's consists of English letters, digits, symbols, and spaces.',
      ],
    },
    {
      id: 'pd-merge-intervals',
      problem_id: PROBLEM_IDS.MERGE_INTERVALS,
      slug: 'merge-intervals',
      summary:
        'Given an array of intervals where intervals[i] = [start_i, end_i], merge all overlapping intervals, and return an array of the non-overlapping intervals that cover all the intervals in the input.',
      companies: ['Google', 'Microsoft', 'Uber'],
      difficulty_rating: 1525,
      updated_at: now,
      follow_up:
        'How would you handle streaming intervals where you cannot keep them all in memory?',
      constraints_json: [
        '$1 \\leq intervals.length \\leq 10^4$',
        'intervals[i].length = 2',
        '$0 \\leq start_i \\leq end_i \\leq 10^4$',
      ],
    },
    {
      id: 'pd-median-two-sorted-arrays',
      problem_id: PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS,
      slug: 'median-of-two-sorted-arrays',
      summary:
        'Given two sorted arrays nums1 and nums2 of size m and n respectively, return the median of the two sorted arrays. The overall run time complexity should be O(log (m+n)).',
      companies: ['Google', 'Uber', 'Microsoft'],
      difficulty_rating: 1960,
      updated_at: now,
      follow_up:
        'Can you prove why the binary search over partitions is correct?',
      constraints_json: [
        '$0 \\leq m, n \\leq 10^6$',
        '$-10^6 \\leq nums1[i], nums2[i] \\leq 10^6$',
        'Runs in O(log(m + n)) time.',
      ],
    },
    {
      id: 'pd-number-of-islands',
      problem_id: PROBLEM_IDS.NUMBER_OF_ISLANDS,
      slug: 'number-of-islands',
      summary:
        'Given an m x n 2D binary grid that represents a map of "1"s (land) and "0"s (water), return the number of islands. An island is surrounded by water and is formed by connecting adjacent lands horizontally or vertically.',
      companies: ['Amazon', 'Microsoft', 'Bloomberg'],
      difficulty_rating: 1620,
      updated_at: now,
      follow_up:
        'How would you count islands in an online grid where cells flip from water to land?',
      constraints_json: [
        '$1 \\leq m, n \\leq 300$',
        'grid[i][j] is "0" or "1".',
      ],
    },
    {
      id: 'pd-combine-two-tables',
      problem_id: PROBLEM_IDS.COMBINE_TWO_TABLES,
      slug: 'combine-two-tables',
      summary:
        'Write a SQL query to report the first name, last name, city, and state of each person in the Person table. If the address of a personId is not present in the Address table, report null instead.',
      companies: ['Amazon', 'Apple', 'Google'],
      difficulty_rating: 1100,
      updated_at: now,
      follow_up: null,
      constraints_json: ['The tables Person and Address exist.'],
    },
    {
      id: 'pd-tenth-line',
      problem_id: PROBLEM_IDS.TENTH_LINE,
      slug: 'tenth-line',
      summary: 'Given a text file `file.txt`, print just the 10th line of the file.',
      companies: ['Google', 'Amazon', 'Facebook'],
      difficulty_rating: 1200,
      updated_at: now,
      follow_up: null,
      constraints_json: ['file.txt exists.'],
    },
    {
      id: 'pd-print-foobar',
      problem_id: PROBLEM_IDS.PRINT_FOOBAR_ALTERNATELY,
      slug: 'print-foobar-alternately',
      summary:
        'Suppose you are given the following code... The same instance of FooBar will be passed to two different threads. Thread A will call foo() and thread B will call bar(). Modify the program to output "foobar" n times.',
      companies: ['Google', 'Amazon', 'Microsoft'],
      difficulty_rating: 1500,
      updated_at: now,
      follow_up: null,
      constraints_json: ['n is an integer.'],
    },
  ],
  problem_examples: [
    {
      id: 'ex-two-sum-1',
      problem_id: PROBLEM_IDS.TWO_SUM,
      example_order: 0,
      input_text: 'nums = [2,7,11,15], target = 9',
      output_text: '[0,1]',
      explanation: 'Because nums[0] + nums[1] == 9, we return [0, 1].',
    },
    {
      id: 'ex-two-sum-2',
      problem_id: PROBLEM_IDS.TWO_SUM,
      example_order: 1,
      input_text: 'nums = [3,2,4], target = 6',
      output_text: '[1,2]',
      explanation: 'nums[1] + nums[2] == 6, so we return [1, 2].',
    },
    {
      id: 'ex-two-sum-3',
      problem_id: PROBLEM_IDS.TWO_SUM,
      example_order: 2,
      input_text: 'nums = [3,3], target = 6',
      output_text: '[0,1]',
      explanation:
        'The same element cannot be used twice, but two different elements with value 3 can be used.',
    },
    {
      id: 'ex-longest-sub-1',
      problem_id: PROBLEM_IDS.LONGEST_SUBSTRING,
      example_order: 0,
      input_text: 's = "abcabcbb"',
      output_text: '3',
      explanation: 'The answer is "abc", with the length of 3.',
    },
    {
      id: 'ex-longest-sub-2',
      problem_id: PROBLEM_IDS.LONGEST_SUBSTRING,
      example_order: 1,
      input_text: 's = "bbbbb"',
      output_text: '1',
      explanation: 'The answer is "b", with the length of 1.',
    },
    {
      id: 'ex-longest-sub-3',
      problem_id: PROBLEM_IDS.LONGEST_SUBSTRING,
      example_order: 2,
      input_text: 's = "pwwkew"',
      output_text: '3',
      explanation:
        'The answer is "wke", with the length of 3. Note that the answer must be a substring, "pwke" is a subsequence and not a substring.',
    },
    {
      id: 'ex-merge-1',
      problem_id: PROBLEM_IDS.MERGE_INTERVALS,
      example_order: 0,
      input_text: 'intervals = [[1,3],[2,6],[8,10],[15,18]]',
      output_text: '[[1,6],[8,10],[15,18]]',
      explanation: 'Intervals [1,3] and [2,6] overlap, merge into [1,6].',
    },
    {
      id: 'ex-merge-2',
      problem_id: PROBLEM_IDS.MERGE_INTERVALS,
      example_order: 1,
      input_text: 'intervals = [[1,4],[4,5]]',
      output_text: '[[1,5]]',
      explanation: 'Intervals that touch at the boundary are merged.',
    },
    {
      id: 'ex-merge-3',
      problem_id: PROBLEM_IDS.MERGE_INTERVALS,
      example_order: 2,
      input_text: 'intervals = [[1,4],[2,3]]',
      output_text: '[[1,4]]',
      explanation: 'The second interval is contained within the first.',
    },
    {
      id: 'ex-median-1',
      problem_id: PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS,
      example_order: 0,
      input_text: 'nums1 = [1,3], nums2 = [2]',
      output_text: '2.00000',
      explanation: 'Merged array is [1,2,3] and median is 2.',
    },
    {
      id: 'ex-median-2',
      problem_id: PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS,
      example_order: 1,
      input_text: 'nums1 = [1,2], nums2 = [3,4]',
      output_text: '2.50000',
      explanation: 'Merged array is [1,2,3,4] and median is (2 + 3) / 2 = 2.5.',
    },
    {
      id: 'ex-median-3',
      problem_id: PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS,
      example_order: 2,
      input_text: 'nums1 = [], nums2 = [1]',
      output_text: '1.00000',
      explanation: 'Median is the only element 1.',
    },
    {
      id: 'ex-islands-1',
      problem_id: PROBLEM_IDS.NUMBER_OF_ISLANDS,
      example_order: 0,
      input_text:
        'grid = [["1","1","1","1","0"],["1","1","0","1","0"],["1","1","0","0","0"],["0","0","0","0","0"]]',
      output_text: '1',
      explanation: 'All land cells are connected into a single island.',
    },
    {
      id: 'ex-islands-2',
      problem_id: PROBLEM_IDS.NUMBER_OF_ISLANDS,
      example_order: 1,
      input_text:
        'grid = [["1","1","0","0","0"],["1","1","0","0","0"],["0","0","1","0","0"],["0","0","0","1","1"]]',
      output_text: '3',
      explanation:
        'There is one island in the top-left, one in the middle, and one in the bottom-right.',
    },
    {
      id: 'ex-islands-3',
      problem_id: PROBLEM_IDS.NUMBER_OF_ISLANDS,
      example_order: 2,
      input_text: 'grid = [["0","0","0"],["0","0","0"],["0","0","0"]]',
      output_text: '0',
      explanation: 'No land cells, so zero islands.',
    },
  ],
  problem_approaches: [
    {
      id: 'ap-two-sum-hashmap',
      problem_id: PROBLEM_IDS.TWO_SUM,
      title: 'One-pass Hash Table',
      summary:
        "It turns out we can do it in one-pass. While we iterate and inserting elements into the table, we also look back to check if current element's complement already exists in the table. If it exists, we have found a solution and return immediately.",
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
      problem_id: PROBLEM_IDS.TWO_SUM,
      label: 'TypeScript',
      value: 'typescript',
      starterCode:
        'function twoSum(nums: number[], target: number): number[] {\n    \n};',
    },
    {
      id: 'lang-js',
      problem_id: PROBLEM_IDS.TWO_SUM,
      label: 'JavaScript',
      value: 'javascript',
      starterCode:
        '/**\n * @param {number[]} nums\n * @param {number} target\n * @return {number[]}\n */\nvar twoSum = function(nums, target) {\n    \n};',
    },
    {
      id: 'lang-py',
      problem_id: PROBLEM_IDS.TWO_SUM,
      label: 'Python',
      value: 'python',
      starterCode:
        'class Solution:\n    def twoSum(self, nums: List[int], target: int) -> List[int]:\n        ',
    },
    {
      id: 'lang-java',
      problem_id: PROBLEM_IDS.TWO_SUM,
      label: 'Java',
      value: 'java',
      starterCode:
        'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        \n    }\n}',
    },
    {
      id: 'lang-cpp',
      problem_id: PROBLEM_IDS.TWO_SUM,
      label: 'C++',
      value: 'cpp',
      starterCode:
        'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        \n    }\n};',
    },
    {
      id: 'lang-ts-2',
      problem_id: PROBLEM_IDS.LONGEST_SUBSTRING,
      label: 'TypeScript',
      value: 'typescript',
      starterCode:
        'function lengthOfLongestSubstring(s: string): number {\n    \n};',
    },
    {
      id: 'lang-py-2',
      problem_id: PROBLEM_IDS.LONGEST_SUBSTRING,
      label: 'Python',
      value: 'python',
      starterCode:
        'class Solution:\n    def lengthOfLongestSubstring(self, s: str) -> int:\n        ',
    },
    {
      id: 'lang-java-2',
      problem_id: PROBLEM_IDS.LONGEST_SUBSTRING,
      label: 'Java',
      value: 'java',
      starterCode:
        'class Solution {\n    public int lengthOfLongestSubstring(String s) {\n        \n    }\n}',
    },
    {
      id: 'lang-ts-3',
      problem_id: PROBLEM_IDS.MERGE_INTERVALS,
      label: 'TypeScript',
      value: 'typescript',
      starterCode:
        'function merge(intervals: number[][]): number[][] {\n    \n};',
    },
    {
      id: 'lang-py-3',
      problem_id: PROBLEM_IDS.MERGE_INTERVALS,
      label: 'Python',
      value: 'python',
      starterCode:
        'class Solution:\n    def merge(self, intervals: List[List[int]]) -> List[List[int]]:\n        ',
    },
    {
      id: 'lang-java-3',
      problem_id: PROBLEM_IDS.MERGE_INTERVALS,
      label: 'Java',
      value: 'java',
      starterCode:
        'class Solution {\n    public int[][] merge(int[][] intervals) {\n        \n    }\n}',
    },
    {
      id: 'lang-ts-4',
      problem_id: PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS,
      label: 'TypeScript',
      value: 'typescript',
      starterCode:
        'function findMedianSortedArrays(nums1: number[], nums2: number[]): number {\n    \n};',
    },
    {
      id: 'lang-py-4',
      problem_id: PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS,
      label: 'Python',
      value: 'python',
      starterCode:
        'class Solution:\n    def findMedianSortedArrays(self, nums1: List[int], nums2: List[int]) -> float:\n        ',
    },
    {
      id: 'lang-java-4',
      problem_id: PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS,
      label: 'Java',
      value: 'java',
      starterCode:
        'class Solution {\n    public double findMedianSortedArrays(int[] nums1, int[] nums2) {\n        \n    }\n}',
    },
    {
      id: 'lang-ts-5',
      problem_id: PROBLEM_IDS.NUMBER_OF_ISLANDS,
      label: 'TypeScript',
      value: 'typescript',
      starterCode: 'function numIslands(grid: string[][]): number {\n    \n};',
    },
    {
      id: 'lang-py-5',
      problem_id: PROBLEM_IDS.NUMBER_OF_ISLANDS,
      label: 'Python',
      value: 'python',
      starterCode:
        'class Solution:\n    def numIslands(self, grid: List[List[str]]) -> int:\n        ',
    },
    {
      id: 'lang-java-5',
      problem_id: PROBLEM_IDS.NUMBER_OF_ISLANDS,
      label: 'Java',
      value: 'java',
      starterCode:
        'class Solution {\n    public int numIslands(char[][] grid) {\n        \n    }\n}',
    },
  ],
} as const;

export default data;
