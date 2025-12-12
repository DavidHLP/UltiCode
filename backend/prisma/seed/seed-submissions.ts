import { PrismaClient, Prisma } from '@prisma/client';
import { USER_IDS } from './data/users.data';

const SUBMISSIONS: Prisma.SubmissionCreateInput[] = [
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
    created_at: new Date(Date.now() - 1000 * 60 * 30), // 30 mins ago
  },
];

export async function clearSubmissions(prisma: PrismaClient) {
  console.log('  🗑️ Clearing submissions...');
  await prisma.submission.deleteMany();
}

export async function seedSubmissions(prisma: PrismaClient) {
  console.log('  🌱 Seeding submissions...');
  for (const sub of SUBMISSIONS) {
    await prisma.submission.create({ data: sub });
  }
  return { count: SUBMISSIONS.length };
}
