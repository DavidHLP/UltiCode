import { Prisma } from '@prisma/client';
import { USER_IDS } from './users.data';

export const SUBMISSIONS: Prisma.SubmissionCreateInput[] = [
  {
    id: 'sub-001',
    problem: { connect: { id: 1n } },
    user: { connect: { id: USER_IDS.YUKI } },
    language: 'Python',
    code: `def twoSum(nums, target):
    seen = {}
    for i, num in enumerate(nums):
        complement = target - num
        if complement in seen:
            return [seen[complement], i]
        seen[num] = i
    return []`,
    status: 'Accepted',
    runtime: 45,
    memory: 14.2,
    runtime_percentile: 85.5,
    memory_percentile: 40.2,
    test_details: [
      { status: 'Accepted', time: 10, memory: 14.1 },
      { status: 'Accepted', time: 12, memory: 14.2 },
      { status: 'Accepted', time: 23, memory: 14.2 },
    ],
    runtimeDistBinsMs: [
      { min: 38, count: 2 },
      { min: 39, count: 5 },
      { min: 40, count: 12 },
      { min: 41, count: 15 },
      { min: 42, count: 28 },
      { min: 43, count: 45 },
      { min: 44, count: 68 },
      { min: 45, count: 32 },
      { min: 46, count: 18 },
      { min: 47, count: 10 },
      { min: 48, count: 4 },
    ],
    memoryDistBinsMb: [
      { min: 13.5, count: 5 },
      { min: 13.6, count: 12 },
      { min: 13.7, count: 35 },
      { min: 13.8, count: 48 },
      { min: 13.9, count: 22 },
      { min: 14.0, count: 15 },
      { min: 14.1, count: 8 },
      { min: 14.2, count: 3 },
    ],
    created_at: new Date(Date.now() - 1000 * 60 * 60 * 2), // 2 hours ago
  },
  {
    id: 'sub-002',
    problem: { connect: { id: 1n } },
    user: { connect: { id: USER_IDS.YUKI } },
    language: 'Python',
    code: `def twoSum(nums, target):
    for i in range(len(nums)):
        for j in range(i + 1, len(nums)):
            if nums[i] + nums[j] == target:
                return [i, j]
    return []`,
    status: 'Time Limit Exceeded',
    runtime: 5000,
    memory: 13.8,
    created_at: new Date(Date.now() - 1000 * 60 * 60 * 24), // 1 day ago
  },
  {
    id: 'sub-003',
    problem: { connect: { id: 1n } },
    user: { connect: { id: USER_IDS.DAVID } },
    language: 'C++',
    code: `class Solution {
public:
    vector<int> twoSum(vector<int>& nums, int target) {
        unordered_map<int, int> seen;
        for (int i = 0; i < nums.size(); ++i) {
            int complement = target - nums[i];
            if (seen.count(complement)) {
                return {seen[complement], i};
            }
            seen[nums[i]] = i;
        }
        return {};
    }
};`,
    status: 'Accepted',
    runtime: 3,
    memory: 8.5,
    runtime_percentile: 98.2,
    memory_percentile: 70.5,
    runtimeDistBinsMs: [
      { min: 2, count: 5 },
      { min: 3, count: 42 },
      { min: 4, count: 15 },
      { min: 5, count: 8 },
    ],
    memoryDistBinsMb: [
      { min: 8.2, count: 2 },
      { min: 8.3, count: 8 },
      { min: 8.4, count: 25 },
      { min: 8.5, count: 40 },
      { min: 8.6, count: 12 },
    ],
    created_at: new Date(Date.now() - 1000 * 60 * 30), // 30 mins ago
  },
  // Add submission for Alex (matching sol-002 JavaScript)
  {
    id: 'sub-004',
    problem: { connect: { id: 1n } },
    user: { connect: { id: USER_IDS.ALEX } },
    language: 'JavaScript',
    code: `function twoSum(nums, target) {
    for (let i = 0; i < nums.length; i++) {
        for (let j = i + 1; j < nums.length; j++) {
            if (nums[i] + nums[j] === target) {
                return [i, j];
            }
        }
    }
    return [];
}`,
    status: 'Accepted',
    runtime: 120,
    memory: 34.5,
    runtime_percentile: 15.5,
    memory_percentile: 90.2,
    created_at: new Date(Date.now() - 1000 * 60 * 60 * 24 * 2), // 2 days ago
  },
  // Add submission for Tourist (matching sol-004 Java)
  {
    id: 'sub-005',
    problem: { connect: { id: 1n } },
    user: { connect: { id: USER_IDS.TOURIST } },
    language: 'Java',
    code: `class Solution {
    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) {
                return new int[] { map.get(complement), i };
            }
            map.put(nums[i], i);
        }
        throw new IllegalArgumentException("No solution");
    }
}`,
    status: 'Accepted',
    runtime: 2,
    memory: 42.1,
    runtime_percentile: 95.8,
    memory_percentile: 45.2,
    created_at: new Date(Date.now() - 1000 * 60 * 60 * 5), // 5 hours ago
  },
];
