// prisma/seed/data/solutions.data.ts
// prisma/seed/data/solutions.data.ts
import { USER_IDS } from './users.data';
import { PROBLEM_IDS } from './problems.data';

export const SOLUTION_IDS = {
  TWO_SUM_OPTIMAL: 'sol-001',
  TWO_SUM_BRUTE: 'sol-002',
  TWO_SUM_CPP: 'sol-003',
  TWO_SUM_JAVA: 'sol-004',
  LONGEST_SUBSTR_SLIDING: 'sol-005',
  MERGE_INTERVALS_SORT: 'sol-006',
  MEDIAN_ARRAYS_BS: 'sol-007',
  ISLANDS_DFS: 'sol-008',
} as const;

export const COMMENT_IDS = {
  TWO_SUM_OPT_1: 'comment-001',
  TWO_SUM_OPT_2: 'comment-002',
  TWO_SUM_OPT_3: 'comment-003',
  TWO_SUM_OPT_4: 'comment-004',
  TWO_SUM_BRUTE_1: 'comment-005',
  TWO_SUM_CPP_1: 'comment-006',
  LONGEST_SUBSTR_1: 'comment-007',
  MERGE_INT_1: 'comment-008',
  MEDIAN_BS_1: 'comment-009',
  ISLANDS_DFS_1: 'comment-010',
} as const;

const data = {
  solutions: [
    {
      id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      problem_id: PROBLEM_IDS.TWO_SUM,
      user_id: USER_IDS.YUKI,
      title: 'Hash Map Approach - O(n) Time Complexity',
      content: `# Hash Map Solution

This solution uses a hash map to solve the Two Sum problem in O(n) time.

## Approach

1. Create a hash map to store numbers and their indices
2. Iterate through the array once
3. For each element, check if the complement exists in the hash map
4. If found, return the indices

## Code

\`\`\`typescript {group="sol-001"}
function twoSum(nums: number[], target: number): number[] {
    const seen = new Map<number, number>();
    for (let i = 0; i < nums.length; i++) {
        const complement = target - nums[i];
        if (seen.has(complement)) {
            return [seen.get(complement)!, i];
        }
        seen.set(nums[i], i);
    }
    return [];
}
\`\`\`

## Complexity Analysis

- **Time Complexity**: O(n) - Single pass through the array
- **Space Complexity**: O(n) - Hash map storage

## Why This Works

By storing each number as we iterate, we can check for complements in constant time, making this solution optimal.`,
      summary:
        'Efficient O(n) solution using hash map to find two numbers that add up to target',
      language: 'TypeScript',
      tags: ['Hash Table', 'Array', 'Two Pointers'],

    },
    {
      id: SOLUTION_IDS.TWO_SUM_BRUTE,
      problem_id: PROBLEM_IDS.TWO_SUM,
      user_id: USER_IDS.ALEX,
      title: 'Brute Force Solution - Easy to Understand',
      content: `# Brute Force Approach

A straightforward solution checking all pairs.

## Approach

Check every possible pair of numbers to see if they sum to the target.

\`\`\`javascript {group="sol-002"}
function twoSum(nums, target) {
    for (let i = 0; i < nums.length; i++) {
        for (let j = i + 1; j < nums.length; j++) {
            if (nums[i] + nums[j] === target) {
                return [i, j];
            }
        }
    }
    return [];
}
\`\`\`

## Complexity

- **Time**: O(n²)
- **Space**: O(1)

Not optimal but easy to understand!`,
      summary:
        'Simple brute force approach checking all pairs - good for beginners',
      language: 'JavaScript',
      tags: ['Array', 'Brute Force'],
    },
    {
      id: SOLUTION_IDS.TWO_SUM_CPP,
      problem_id: PROBLEM_IDS.TWO_SUM,
      user_id: USER_IDS.CHEN,
      title: 'TypeScript Map Solution',
      content: `# TypeScript Solution

Using a Map for a clean and efficient solution.

\`\`\`typescript {group="sol-003"}
function twoSum(nums: number[], target: number): number[] {
    const seen = new Map<number, number>();
    for (let i = 0; i < nums.length; i++) {
        const complement = target - nums[i];
        if (seen.has(complement)) {
            return [seen.get(complement)!, i];
        }
        seen.set(nums[i], i);
    }
    return [];
}
\`\`\`

Fast and memory efficient with a Map!`,
      summary: 'TypeScript implementation using Map for O(n) solution',
      language: 'TypeScript',
      tags: ['Hash Table', 'TypeScript', 'Array'],
    },
    {
      id: SOLUTION_IDS.TWO_SUM_JAVA,
      problem_id: PROBLEM_IDS.TWO_SUM,
      user_id: USER_IDS.TOURIST,
      title: 'JavaScript Hash Map Implementation',
      content: `# JavaScript Solution

\`\`\`javascript {group="sol-004"}
function twoSum(nums, target) {
    const map = new Map();
    for (let i = 0; i < nums.length; i++) {
        const complement = target - nums[i];
        if (map.has(complement)) {
            return [map.get(complement), i];
        }
        map.set(nums[i], i);
    }
    throw new Error("No solution");
}
\`\`\``,
      summary: 'Clean JavaScript implementation with Map',
      language: 'JavaScript',
      tags: ['Hash Table', 'JavaScript'],
    },
    {
      id: SOLUTION_IDS.LONGEST_SUBSTR_SLIDING,
      problem_id: PROBLEM_IDS.LONGEST_SUBSTRING,
      user_id: USER_IDS.SARA,
      title: 'Sliding Window with Last-Seen Map',
      content: `# Sliding Window

Use left and right pointers with a hashmap of last seen indices to maintain a valid window.

\`\`\`typescript {group="sol-005"}
function lengthOfLongestSubstring(s: string): number {
    const seen = new Map<string, number>();
    let left = 0;
    let best = 0;
    for (let right = 0; right < s.length; right++) {
        const ch = s[right];
        if (seen.has(ch) && seen.get(ch)! >= left) {
            left = seen.get(ch)! + 1;
        }
        seen.set(ch, right);
        best = Math.max(best, right - left + 1);
    }
    return best;
}
\`\`\`

Runs in O(n) time and O(k) space where k is the alphabet size.`,
      summary:
        'Classic sliding window that bumps the left pointer past duplicates.',
      language: 'TypeScript',
      tags: ['Sliding Window', 'Hash Table', 'String'],
    },
    {
      id: SOLUTION_IDS.MERGE_INTERVALS_SORT,
      problem_id: PROBLEM_IDS.MERGE_INTERVALS,
      user_id: USER_IDS.MAX,
      title: 'Sort and Sweep Merge',
      content: `# Sort then sweep

1. Sort intervals by start.
2. Grow the current interval while there is overlap.
3. Push merged results.

\`\`\`javascript {group="sol-006"}
var merge = function(intervals) {
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
};
\`\`\`

Sorting dominates the time complexity.`,
      summary:
        'Greedy sweep after sorting intervals by start; merge in one pass',
      language: 'JavaScript',
      tags: ['Sorting', 'Intervals', 'Greedy'],
    },
    {
      id: SOLUTION_IDS.MEDIAN_ARRAYS_BS,
      problem_id: PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS,
      user_id: USER_IDS.PETR,
      title: 'Binary Search on Partitions',
      content: `# Binary Search on Partitions

We binary search the cut on the shorter array so that left parts contain half the elements and all left values are <= all right values.

\`\`\`typescript {group="sol-007"}
function findMedianSortedArrays(nums1: number[], nums2: number[]): number {
    if (nums1.length > nums2.length) {
        [nums1, nums2] = [nums2, nums1];
    }
    const m = nums1.length;
    const n = nums2.length;
    const total = m + n;
    const half = Math.floor(total / 2);
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
            if (total % 2) {
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
}
\`\`\`

Binary search over the smaller array keeps complexity O(log(min(m, n))).`,
      summary:
        'Binary search the cut on the shorter array to balance partitions',
      language: 'TypeScript',
      tags: ['Binary Search', 'Divide and Conquer'],
    },
    {
      id: SOLUTION_IDS.ISLANDS_DFS,
      problem_id: PROBLEM_IDS.NUMBER_OF_ISLANDS,
      user_id: USER_IDS.CHEN,
      title: 'Iterative DFS Flood Fill',
      content: `# DFS Flood Fill

Mark visited land cells and explore four directions using an explicit stack to avoid recursion depth issues.

\`\`\`typescript {group="sol-008"}
function numIslands(grid: string[][]): number {
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
}
\`\`\`

Space can be reduced by marking the grid in place instead of using a visited matrix.`,
      summary:
        'Iterative DFS flood fill to mark connected land and count components',
      language: 'TypeScript',
      tags: ['DFS', 'Matrix', 'Graph'],
    },
  ],
  comments: [
    {
      id: COMMENT_IDS.TWO_SUM_OPT_1,
      solution_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      parent_id: null,
      user_id: USER_IDS.MAX,
      content: 'Great explanation! This helped me understand hash maps better.',

    },
    {
      id: COMMENT_IDS.TWO_SUM_OPT_2,
      solution_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      parent_id: COMMENT_IDS.TWO_SUM_OPT_1,
      user_id: USER_IDS.YUKI,
      content: 'Thanks! Glad it was helpful 😊',

    },
    {
      id: COMMENT_IDS.TWO_SUM_OPT_3,
      solution_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      parent_id: null,
      user_id: USER_IDS.SARA,
      content: 'What if there are duplicate numbers in the array?',

    },
    {
      id: COMMENT_IDS.TWO_SUM_OPT_4,
      solution_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      parent_id: COMMENT_IDS.TWO_SUM_OPT_3,
      user_id: USER_IDS.YUKI,
      content:
        "Good question! The hash map will overwrite the previous index, but that's fine since we only need to find one valid pair.",

    },
    {
      id: COMMENT_IDS.TWO_SUM_BRUTE_1,
      solution_id: SOLUTION_IDS.TWO_SUM_BRUTE,
      parent_id: null,
      user_id: USER_IDS.LILY,
      content: 'This is a good starting point for beginners!',

    },
    {
      id: COMMENT_IDS.TWO_SUM_CPP_1,
      solution_id: SOLUTION_IDS.TWO_SUM_CPP,
      parent_id: null,
      user_id: USER_IDS.DAVID,
      content: 'Love the TypeScript Map approach, very clean!',

    },
    {
      id: COMMENT_IDS.LONGEST_SUBSTR_1,
      solution_id: SOLUTION_IDS.LONGEST_SUBSTR_SLIDING,
      parent_id: null,
      user_id: USER_IDS.TOM,
      content:
        'Nice explanation of how to move the left pointer; fixed my own off-by-one.',

    },
    {
      id: COMMENT_IDS.MERGE_INT_1,
      solution_id: SOLUTION_IDS.MERGE_INTERVALS_SORT,
      parent_id: null,
      user_id: USER_IDS.LILY,
      content:
        'Sorting first is underrated here; this is faster than my interval tree attempt.',

    },
    {
      id: COMMENT_IDS.MEDIAN_BS_1,
      solution_id: SOLUTION_IDS.MEDIAN_ARRAYS_BS,
      parent_id: null,
      user_id: USER_IDS.SCOTT,
      content: 'Binary search proof sketch was useful, thanks!',

    },
    {
      id: COMMENT_IDS.ISLANDS_DFS_1,
      solution_id: SOLUTION_IDS.ISLANDS_DFS,
      parent_id: null,
      user_id: USER_IDS.EMMA,
      content:
        'Stack-based DFS kept my recursion stack from blowing up. Good tip.',

    },
  ],
} as const;

export default data;
