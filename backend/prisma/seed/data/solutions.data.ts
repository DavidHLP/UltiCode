// prisma/seed/data/solutions.data.ts
// prisma/seed/data/solutions.data.ts
import { USER_IDS } from './users.data';

const data = {
  solutions: [
    {
      id: 'sol-001',
      problem_id: 1,
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

\`\`\`python {group="sol-001"}
def twoSum(nums, target):
    seen = {}
    for i, num in enumerate(nums):
        complement = target - num
        if complement in seen:
            return [seen[complement], i]
        seen[num] = i
    return []
\`\`\`
\`\`\`java {group="sol-001"}
class Solution {
    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) {
                return new int[] { map.get(complement), i };
            }
            map.put(nums[i], i);
        }
        return new int[0];
    }
}
\`\`\`
\`\`\`cpp {group="sol-001"}
class Solution {
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
};
\`\`\`

## Complexity Analysis

- **Time Complexity**: O(n) - Single pass through the array
- **Space Complexity**: O(n) - Hash map storage

## Why This Works

By storing each number as we iterate, we can check for complements in constant time, making this solution optimal.`,
      summary:
        'Efficient O(n) solution using hash map to find two numbers that add up to target',
      language: 'Python',
      tags: ['Hash Table', 'Array', 'Two Pointers'],
      views: 1250,
      likes: 340,
      dislikes: 12,
    },
    {
      id: 'sol-002',
      problem_id: 1,
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
\`\`\`python {group="sol-002"}
def twoSum(nums, target):
    for i in range(len(nums)):
        for j in range(i + 1, len(nums)):
            if nums[i] + nums[j] == target:
                return [i, j]
    return []
\`\`\`
\`\`\`java {group="sol-002"}
class Solution {
    public int[] twoSum(int[] nums, int target) {
        for (int i = 0; i < nums.length; i++) {
            for (int j = i + 1; j < nums.length; j++) {
                if (nums[i] + nums[j] == target) {
                    return new int[] { i, j };
                }
            }
        }
        return new int[0];
    }
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
      views: 850,
      likes: 125,
      dislikes: 35,
    },
    {
      id: 'sol-003',
      problem_id: 1,
      user_id: USER_IDS.CHEN,
      title: 'C++ STL unordered_map Solution',
      content: `# C++ Solution with STL

Using C++ STL for a clean and efficient solution.

\`\`\`cpp {group="sol-003"}
class Solution {
public:
    vector<int> twoSum(vector<int>& nums, int target) {
        unordered_map<int, int> seen;
        for (int i = 0; i < nums.size(); i++) {
            int complement = target - nums[i];
            if (seen.find(complement) != seen.end()) {
                return {seen[complement], i};
            }
            seen[nums[i]] = i;
        }
        return {};
    }
};
\`\`\`
\`\`\`python {group="sol-003"}
def twoSum(nums, target):
    seen = {}
    for i, num in enumerate(nums):
        complement = target - num
        if complement in seen:
            return [seen[complement], i]
        seen[num] = i
    return []
\`\`\`
\`\`\`java {group="sol-003"}
class Solution {
    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) {
                return new int[] { map.get(complement), i };
            }
            map.put(nums[i], i);
        }
        return new int[0];
    }
}
\`\`\`

Fast and memory efficient with C++ STL!`,
      summary: 'C++ implementation using unordered_map for O(n) solution',
      language: 'C++',
      tags: ['Hash Table', 'C++', 'STL'],
      views: 620,
      likes: 89,
      dislikes: 5,
    },
    {
      id: 'sol-004',
      problem_id: 1,
      user_id: USER_IDS.TOURIST,
      title: 'Java HashMap Implementation',
      content: `# Java Solution

\`\`\`java {group="sol-004"}
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
\`\`\`
\`\`\`python {group="sol-004"}
def twoSum(nums, target):
    seen = {}
    for i, num in enumerate(nums):
        complement = target - num
        if complement in seen:
            return [seen[complement], i]
        seen[num] = i
    return []
\`\`\`
\`\`\`cpp {group="sol-004"}
class Solution {
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
};
\`\`\``,
      summary: 'Clean Java implementation with HashMap',
      language: 'Java',
      tags: ['Hash Table', 'Java'],
      views: 480,
      likes: 67,
      dislikes: 3,
    },
    {
      id: 'sol-005',
      problem_id: 2,
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
      views: 1020,
      likes: 210,
      dislikes: 9,
    },
    {
      id: 'sol-006',
      problem_id: 3,
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
      summary: 'Greedy sweep after sorting intervals by start; merge in one pass',
      language: 'JavaScript',
      tags: ['Sorting', 'Intervals', 'Greedy'],
      views: 740,
      likes: 132,
      dislikes: 6,
    },
    {
      id: 'sol-007',
      problem_id: 4,
      user_id: USER_IDS.PETR,
      title: 'Binary Search on Partitions',
      content: `# Binary Search on Partitions

We binary search the cut on the shorter array so that left parts contain half the elements and all left values are <= all right values.

\`\`\`python {group="sol-007"}
from typing import List

class Solution:
    def findMedianSortedArrays(self, nums1: List[int], nums2: List[int]) -> float:
        if len(nums1) > len(nums2):
            nums1, nums2 = nums2, nums1
        m, n = len(nums1), len(nums2)
        total = m + n
        half = total // 2
        lo, hi = 0, m
        while lo <= hi:
            i = (lo + hi) // 2
            j = half - i
            left1 = nums1[i - 1] if i > 0 else float('-inf')
            right1 = nums1[i] if i < m else float('inf')
            left2 = nums2[j - 1] if j > 0 else float('-inf')
            right2 = nums2[j] if j < n else float('inf')
            if left1 <= right2 and left2 <= right1:
                if total % 2:
                    return min(right1, right2)
                return (max(left1, left2) + min(right1, right2)) / 2
            if left1 > right2:
                hi = i - 1
            else:
                lo = i + 1
        return 0.0
\`\`\`

Binary search over the smaller array keeps complexity O(log(min(m, n))).`,
      summary: 'Binary search the cut on the shorter array to balance partitions',
      language: 'Python',
      tags: ['Binary Search', 'Divide and Conquer'],
      views: 980,
      likes: 180,
      dislikes: 21,
    },
    {
      id: 'sol-008',
      problem_id: 5,
      user_id: USER_IDS.CHEN,
      title: 'Iterative DFS Flood Fill',
      content: `# DFS Flood Fill

Mark visited land cells and explore four directions using an explicit stack to avoid recursion depth issues.

\`\`\`java {group="sol-008"}
class Solution {
    public int numIslands(char[][] grid) {
        int m = grid.length, n = grid[0].length;
        boolean[][] seen = new boolean[m][n];
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        int count = 0;
        for (int r = 0; r < m; r++) {
            for (int c = 0; c < n; c++) {
                if (grid[r][c] == '1' && !seen[r][c]) {
                    count++;
                    Deque<int[]> stack = new ArrayDeque<>();
                    stack.push(new int[]{r, c});
                    seen[r][c] = true;
                    while (!stack.isEmpty()) {
                        int[] cur = stack.pop();
                        for (int[] d : dirs) {
                            int nr = cur[0] + d[0];
                            int nc = cur[1] + d[1];
                            if (nr >= 0 && nr < m && nc >= 0 && nc < n && grid[nr][nc] == '1' && !seen[nr][nc]) {
                                seen[nr][nc] = true;
                                stack.push(new int[]{nr, nc});
                            }
                        }
                    }
                }
            }
        }
        return count;
    }
}
\`\`\`

Space can be reduced by marking the grid in place instead of using a visited matrix.`,
      summary:
        'Iterative DFS flood fill to mark connected land and count components',
      language: 'Java',
      tags: ['DFS', 'Matrix', 'Graph'],
      views: 560,
      likes: 140,
      dislikes: 4,
    },
  ],
  comments: [
    {
      id: 'comment-001',
      solution_id: 'sol-001',
      parent_id: null,
      user_id: USER_IDS.MAX,
      content: 'Great explanation! This helped me understand hash maps better.',
      likes: 25,
    },
    {
      id: 'comment-002',
      solution_id: 'sol-001',
      parent_id: 'comment-001',
      user_id: USER_IDS.YUKI,
      content: 'Thanks! Glad it was helpful 😊',
      likes: 8,
    },
    {
      id: 'comment-003',
      solution_id: 'sol-001',
      parent_id: null,
      user_id: USER_IDS.SARA,
      content: 'What if there are duplicate numbers in the array?',
      likes: 15,
    },
    {
      id: 'comment-004',
      solution_id: 'sol-001',
      parent_id: 'comment-003',
      user_id: USER_IDS.YUKI,
      content:
        "Good question! The hash map will overwrite the previous index, but that's fine since we only need to find one valid pair.",
      likes: 20,
    },
    {
      id: 'comment-005',
      solution_id: 'sol-002',
      parent_id: null,
      user_id: USER_IDS.LILY,
      content: 'This is a good starting point for beginners!',
      likes: 12,
    },
    {
      id: 'comment-006',
      solution_id: 'sol-003',
      parent_id: null,
      user_id: USER_IDS.DAVID,
      content: 'Love the C++ STL approach, very clean!',
      likes: 7,
    },
    {
      id: 'comment-007',
      solution_id: 'sol-005',
      parent_id: null,
      user_id: USER_IDS.TOM,
      content:
        'Nice explanation of how to move the left pointer; fixed my own off-by-one.',
      likes: 11,
    },
    {
      id: 'comment-008',
      solution_id: 'sol-006',
      parent_id: null,
      user_id: USER_IDS.LILY,
      content:
        'Sorting first is underrated here; this is faster than my interval tree attempt.',
      likes: 9,
    },
    {
      id: 'comment-009',
      solution_id: 'sol-007',
      parent_id: null,
      user_id: USER_IDS.SCOTT,
      content: 'Binary search proof sketch was useful, thanks!',
      likes: 14,
    },
    {
      id: 'comment-010',
      solution_id: 'sol-008',
      parent_id: null,
      user_id: USER_IDS.EMMA,
      content:
        'Stack-based DFS kept my recursion stack from blowing up. Good tip.',
      likes: 6,
    },
  ],
  votes: [
    // Votes for sol-001 (340 upvotes, 12 downvotes)
    { solution_id: 'sol-001', user_id: USER_IDS.MAX, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.SARA, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.TOM, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.LILY, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.DAVID, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.EMMA, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.KEVIN, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.TOURIST, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.JIANGLY, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.BENQ, vote_type: 1 },

    // Votes for sol-002 (125 upvotes, 35 downvotes)
    { solution_id: 'sol-002', user_id: USER_IDS.LILY, vote_type: 1 },
    { solution_id: 'sol-002', user_id: USER_IDS.EMMA, vote_type: 1 },
    { solution_id: 'sol-002', user_id: USER_IDS.YUKI, vote_type: -1 },
    { solution_id: 'sol-003', user_id: USER_IDS.KEVIN, vote_type: 1 },

    // Votes for sol-004 (67 upvotes, 3 downvotes)
    { solution_id: 'sol-004', user_id: USER_IDS.SARA, vote_type: 1 },
    { solution_id: 'sol-004', user_id: USER_IDS.TOM, vote_type: 1 },
    // Votes for sol-005 (210 upvotes, 9 downvotes)
    { solution_id: 'sol-005', user_id: USER_IDS.SHADCN, vote_type: 1 },
    { solution_id: 'sol-005', user_id: USER_IDS.CHEN, vote_type: 1 },
    { solution_id: 'sol-005', user_id: USER_IDS.SCOTT, vote_type: 1 },
    { solution_id: 'sol-005', user_id: USER_IDS.PETR, vote_type: 1 },
    { solution_id: 'sol-005', user_id: USER_IDS.UM_NIK, vote_type: -1 },

    // Votes for sol-006 (132 upvotes, 6 downvotes)
    { solution_id: 'sol-006', user_id: USER_IDS.STACK_UNWIND, vote_type: 1 },
    { solution_id: 'sol-006', user_id: USER_IDS.ALEX, vote_type: 1 },
    { solution_id: 'sol-006', user_id: USER_IDS.KEVIN, vote_type: 1 },

    // Votes for sol-007 (180 upvotes, 21 downvotes)
    { solution_id: 'sol-007', user_id: USER_IDS.ECNERWALA, vote_type: 1 },
    { solution_id: 'sol-007', user_id: USER_IDS.TOURIST, vote_type: 1 },
    { solution_id: 'sol-007', user_id: USER_IDS.JIANGLY, vote_type: 1 },

    // Votes for sol-008 (140 upvotes, 4 downvotes)
    { solution_id: 'sol-008', user_id: USER_IDS.DAVID, vote_type: 1 },
    { solution_id: 'sol-008', user_id: USER_IDS.YUKI, vote_type: 1 },
    { solution_id: 'sol-008', user_id: USER_IDS.LILY, vote_type: 1 },
  ],
} as const;

export default data;
