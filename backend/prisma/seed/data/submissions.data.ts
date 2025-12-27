import { Prisma } from '@prisma/client';
import { USER_IDS } from './users.data';
import { PROBLEM_IDS } from './problems.data';

export const SUBMISSIONS_IDS = {
  TWO_SUM_PYTHON_ACC: 'sub-001',
  TWO_SUM_PYTHON_TLE: 'sub-002',
  TWO_SUM_CPP_ACC: 'sub-003',
  TWO_SUM_JS_ACC: 'sub-004',
  TWO_SUM_JAVA_ACC: 'sub-005',
  LONGEST_SUBSTR_TS_ACC: 'sub-006',
  LONGEST_SUBSTR_PYTHON_WA: 'sub-007',
  MERGE_INT_JS_ACC: 'sub-008',
  MEDIAN_PYTHON_ACC: 'sub-009',
  ISLANDS_JAVA_ACC: 'sub-010',
} as const;

export const SUBMISSIONS: Prisma.SubmissionCreateInput[] = [
  {
    id: SUBMISSIONS_IDS.TWO_SUM_PYTHON_ACC,
    problem: { connect: { id: BigInt(PROBLEM_IDS.TWO_SUM) } },
    user: { connect: { id: USER_IDS.YUKI } },
    language: 'TypeScript',
    code: `function twoSum(nums: number[], target: number): number[] {
    const seen = new Map<number, number>();
    for (let i = 0; i < nums.length; i++) {
        const complement = target - nums[i];
        if (seen.has(complement)) {
            return [seen.get(complement)!, i];
        }
        seen.set(nums[i], i);
    }
    return [];
}`,
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
    id: SUBMISSIONS_IDS.TWO_SUM_PYTHON_TLE,
    problem: { connect: { id: BigInt(PROBLEM_IDS.TWO_SUM) } },
    user: { connect: { id: USER_IDS.YUKI } },
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
    status: 'Time Limit Exceeded',
    runtime: 5000,
    memory: 13.8,
    created_at: new Date(Date.now() - 1000 * 60 * 60 * 24), // 1 day ago
  },
  {
    id: SUBMISSIONS_IDS.TWO_SUM_CPP_ACC,
    problem: { connect: { id: BigInt(PROBLEM_IDS.TWO_SUM) } },
    user: { connect: { id: USER_IDS.DAVID } },
    language: 'TypeScript',
    code: `function twoSum(nums: number[], target: number): number[] {
    const seen = new Map<number, number>();
    for (let i = 0; i < nums.length; i++) {
        const complement = target - nums[i];
        if (seen.has(complement)) {
            return [seen.get(complement)!, i];
        }
        seen.set(nums[i], i);
    }
    return [];
}`,
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
    id: SUBMISSIONS_IDS.TWO_SUM_JS_ACC,
    problem: { connect: { id: BigInt(PROBLEM_IDS.TWO_SUM) } },
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
  // Add submission for Tourist (matching sol-004 JavaScript)
  {
    id: SUBMISSIONS_IDS.TWO_SUM_JAVA_ACC,
    problem: { connect: { id: BigInt(PROBLEM_IDS.TWO_SUM) } },
    user: { connect: { id: USER_IDS.TOURIST } },
    language: 'JavaScript',
    code: `function twoSum(nums, target) {
    const map = new Map();
    for (let i = 0; i < nums.length; i++) {
        const complement = target - nums[i];
        if (map.has(complement)) {
            return [map.get(complement), i];
        }
        map.set(nums[i], i);
    }
    throw new Error("No solution");
}`,
    status: 'Accepted',
    runtime: 2,
    memory: 42.1,
    runtime_percentile: 95.8,
    memory_percentile: 45.2,
    created_at: new Date(Date.now() - 1000 * 60 * 60 * 5), // 5 hours ago
  },
  {
    id: SUBMISSIONS_IDS.LONGEST_SUBSTR_TS_ACC,
    problem: { connect: { id: BigInt(PROBLEM_IDS.LONGEST_SUBSTRING) } },
    user: { connect: { id: USER_IDS.SARA } },
    language: 'TypeScript',
    code: `function lengthOfLongestSubstring(s: string): number {
    const seen = new Map<string, number>();
    let left = 0, best = 0;
    for (let right = 0; right < s.length; right++) {
        const ch = s[right];
        if (seen.has(ch) && seen.get(ch)! >= left) {
            left = seen.get(ch)! + 1;
        }
        seen.set(ch, right);
        best = Math.max(best, right - left + 1);
    }
    return best;
}`,
    status: 'Accepted',
    runtime: 72,
    memory: 42.7,
    runtime_percentile: 76.2,
    memory_percentile: 58.4,
    created_at: new Date(Date.now() - 1000 * 60 * 60 * 6), // 6 hours ago
  },
  {
    id: SUBMISSIONS_IDS.LONGEST_SUBSTR_PYTHON_WA,
    problem: { connect: { id: BigInt(PROBLEM_IDS.LONGEST_SUBSTRING) } },
    user: { connect: { id: USER_IDS.LILY } },
    language: 'JavaScript',
    code: `function lengthOfLongestSubstring(s) {
    const seen = new Map();
    let left = 0;
    let best = 0;
    for (let right = 0; right < s.length; right++) {
        const ch = s[right];
        if (seen.has(ch)) {
            left = seen.get(ch);
        }
        seen.set(ch, right);
        best = Math.max(best, right - left + 1);
    }
    return best;
}`,
    status: 'Wrong Answer',
    runtime: 1500,
    memory: 54.3,
    notes: 'Window reset bug on repeated prefix caused missed substring.',
    created_at: new Date(Date.now() - 1000 * 60 * 60 * 30), // 30 hours ago
  },
  {
    id: SUBMISSIONS_IDS.MERGE_INT_JS_ACC,
    problem: { connect: { id: BigInt(PROBLEM_IDS.MERGE_INTERVALS) } },
    user: { connect: { id: USER_IDS.MAX } },
    language: 'JavaScript',
    code: `var merge = function(intervals) {
    intervals.sort((a, b) => a[0] - b[0]);
    const res = [];
    for (const [start, end] of intervals) {
        if (!res.length || start > res[res.length - 1][1]) {
            res.push([start, end]);
        } else {
            res[res.length - 1][1] = Math.max(res[res.length - 1][1], end);
        }
    }
    return res;
};`,
    status: 'Accepted',
    runtime: 95,
    memory: 41.2,
    runtime_percentile: 64.5,
    memory_percentile: 71.1,
    created_at: new Date(Date.now() - 1000 * 60 * 90), // 1.5 hours ago
  },
  {
    id: SUBMISSIONS_IDS.MEDIAN_PYTHON_ACC,
    problem: {
      connect: { id: BigInt(PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS) },
    },
    user: { connect: { id: USER_IDS.PETR } },
    language: 'TypeScript',
    code: `function findMedianSortedArrays(nums1: number[], nums2: number[]): number {
    if (nums1.length > nums2.length) {
        [nums1, nums2] = [nums2, nums1];
    }
    const m = nums1.length;
    const n = nums2.length;
    const half = Math.floor((m + n) / 2);
    let lo = 0;
    let hi = m;
    while (lo <= hi) {
        const i = Math.floor((lo + hi) / 2);
        const j = half - i;
        const left1 = i > 0 ? nums1[i - 1] : -Infinity;
        const right1 = i < m ? nums1[i] : Infinity;
        const left2 = j > 0 ? nums2[j - 1] : -Infinity;
        const right2 = j < n ? nums2[j] : Infinity;
        if (left1 <= right2 && left2 <= right1) {
            if ((m + n) % 2) {
                return Math.min(right1, right2);
            }
            return (Math.max(left1, left2) + Math.min(right1, right2)) / 2;
        }
        if (left1 > right2) {
            hi = i - 1;
        } else {
            lo = i + 1;
        }
    }
    return 0;
}`,
    status: 'Accepted',
    runtime: 128,
    memory: 35.4,
    runtime_percentile: 88.3,
    memory_percentile: 63.2,
    created_at: new Date(Date.now() - 1000 * 60 * 60 * 12), // 12 hours ago
  },
  {
    id: SUBMISSIONS_IDS.ISLANDS_JAVA_ACC,
    problem: { connect: { id: BigInt(PROBLEM_IDS.NUMBER_OF_ISLANDS) } },
    user: { connect: { id: USER_IDS.CHEN } },
    language: 'TypeScript',
    code: `function numIslands(grid: string[][]): number {
    const m = grid.length;
    const n = m ? grid[0].length : 0;
    const seen = Array.from({ length: m }, () => Array(n).fill(false));
    const dirs = [
        [1, 0],
        [-1, 0],
        [0, 1],
        [0, -1],
    ];
    let count = 0;
    for (let r = 0; r < m; r++) {
        for (let c = 0; c < n; c++) {
            if (grid[r][c] === "1" && !seen[r][c]) {
                count++;
                const stack: Array<[number, number]> = [[r, c]];
                seen[r][c] = true;
                while (stack.length) {
                    const [cr, cc] = stack.pop()!;
                    for (const [dr, dc] of dirs) {
                        const nr = cr + dr;
                        const nc = cc + dc;
                        if (
                            nr >= 0 &&
                            nr < m &&
                            nc >= 0 &&
                            nc < n &&
                            grid[nr][nc] === "1" &&
                            !seen[nr][nc]
                        ) {
                            seen[nr][nc] = true;
                            stack.push([nr, nc]);
                        }
                    }
                }
            }
        }
    }
    return count;
}`,
    status: 'Accepted',
    runtime: 210,
    memory: 49.3,
    runtime_percentile: 72.4,
    memory_percentile: 44.1,
    created_at: new Date(Date.now() - 1000 * 60 * 60 * 3), // 3 hours ago
  },
];
