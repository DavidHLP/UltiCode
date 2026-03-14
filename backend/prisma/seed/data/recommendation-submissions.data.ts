/**
 * Recommendation system training data - Submissions
 *
 * These submissions create meaningful user behavior patterns for testing
 * the recommendation algorithm.
 *
 * User Learning Patterns:
 * - YUKI (Beginner): Few Easy ACs, Medium attempts fail
 * - ALEX (Balanced): Tries various tags, medium success rate
 * - CHEN (Advanced): Many Medium ACs, attempts Hard
 * - MAX (Weak Point): Strong Array, weak DP
 * - SARA (Biased): Strong String, weak Tree
 * - LILY (Challenger): Many Medium ACs, ready for Hard
 * - DAVID (All-rounder): AC records across all tags
 */

import { Prisma } from '@prisma/client';
import { USER_IDS } from './users.data';
import { PROBLEM_IDS } from './problems.data';
import { REC_PROBLEM_IDS } from './recommendation-problems.data';

// Helper to create dates relative to now
const _hoursAgo = (hours: number) =>
  new Date(Date.now() - hours * 60 * 60 * 1000);
const daysAgo = (days: number) =>
  new Date(Date.now() - days * 24 * 60 * 60 * 1000);

// ============ YUKI (Beginner) ============
// Completed: Easy problems (Two Sum, Valid Parentheses, Merge Two Lists, Climbing Stairs, Valid Anagram)
// Failed: 3Sum (Medium), Group Anagrams (Medium)
export const YUKI_SUBMISSIONS: Prisma.SubmissionCreateInput[] = [
  // Easy ACs
  {
    id: 'rec-yuki-001',
    problem: { connect: { id: BigInt(PROBLEM_IDS.TWO_SUM) } },
    user: { connect: { id: USER_IDS.YUKI } },
    language: 'TypeScript',
    code: `function twoSum(nums: number[], target: number): number[] {
    const map = new Map();
    for (let i = 0; i < nums.length; i++) {
        const diff = target - nums[i];
        if (map.has(diff)) return [map.get(diff), i];
        map.set(nums[i], i);
    }
    return [];
}`,
    status: 'Accepted',
    runtime: 52,
    memory: 15.1,
    runtime_percentile: 78.3,
    memory_percentile: 45.2,
    created_at: daysAgo(7),
  },
  {
    id: 'rec-yuki-002',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.VALID_PARENTHESES) } },
    user: { connect: { id: USER_IDS.YUKI } },
    language: 'TypeScript',
    code: `function isValid(s: string): boolean {
    const stack: string[] = [];
    const map: Record<string, string> = { ')': '(', '}': '{', ']': '[' };
    for (const c of s) {
        if (c in map) {
            if (stack.pop() !== map[c]) return false;
        } else {
            stack.push(c);
        }
    }
    return stack.length === 0;
}`,
    status: 'Accepted',
    runtime: 45,
    memory: 12.3,
    runtime_percentile: 85.2,
    memory_percentile: 52.1,
    created_at: daysAgo(6),
  },
  {
    id: 'rec-yuki-003',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.MERGE_TWO_LISTS) } },
    user: { connect: { id: USER_IDS.YUKI } },
    language: 'TypeScript',
    code: `function mergeTwoLists(list1: ListNode | null, list2: ListNode | null): ListNode | null {
    if (!list1) return list2;
    if (!list2) return list1;
    if (list1.val < list2.val) {
        list1.next = mergeTwoLists(list1.next, list2);
        return list1;
    } else {
        list2.next = mergeTwoLists(list1, list2.next);
        return list2;
    }
}`,
    status: 'Accepted',
    runtime: 68,
    memory: 14.5,
    runtime_percentile: 72.1,
    memory_percentile: 38.5,
    created_at: daysAgo(5),
  },
  {
    id: 'rec-yuki-004',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.CLIMBING_STAIRS) } },
    user: { connect: { id: USER_IDS.YUKI } },
    language: 'TypeScript',
    code: `function climbStairs(n: number): number {
    if (n <= 2) return n;
    let a = 1, b = 2;
    for (let i = 3; i <= n; i++) {
        [a, b] = [b, a + b];
    }
    return b;
}`,
    status: 'Accepted',
    runtime: 38,
    memory: 10.2,
    runtime_percentile: 92.3,
    memory_percentile: 65.4,
    created_at: daysAgo(4),
  },
  {
    id: 'rec-yuki-005',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.VALID_ANAGRAM) } },
    user: { connect: { id: USER_IDS.YUKI } },
    language: 'TypeScript',
    code: `function isAnagram(s: string, t: string): boolean {
    if (s.length !== t.length) return false;
    const count: Record<string, number> = {};
    for (const c of s) count[c] = (count[c] || 0) + 1;
    for (const c of t) {
        if (!count[c]) return false;
        count[c]--;
    }
    return true;
}`,
    status: 'Accepted',
    runtime: 55,
    memory: 13.8,
    runtime_percentile: 75.6,
    memory_percentile: 42.3,
    created_at: daysAgo(3),
  },
  // Failed Medium attempts
  {
    id: 'rec-yuki-006',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.THREE_SUM) } },
    user: { connect: { id: USER_IDS.YUKI } },
    language: 'TypeScript',
    code: `function threeSum(nums: number[]): number[][] {
    const result: number[][] = [];
    for (let i = 0; i < nums.length; i++) {
        for (let j = i + 1; j < nums.length; j++) {
            for (let k = j + 1; k < nums.length; k++) {
                if (nums[i] + nums[j] + nums[k] === 0) {
                    result.push([nums[i], nums[j], nums[k]]);
                }
            }
        }
    }
    return result;
}`,
    status: 'Time Limit Exceeded',
    runtime: 5000,
    memory: 25.3,
    created_at: daysAgo(2),
  },
  {
    id: 'rec-yuki-007',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.GROUP_ANAGRAMS) } },
    user: { connect: { id: USER_IDS.YUKI } },
    language: 'TypeScript',
    code: `function groupAnagrams(strs: string[]): string[][] {
    const result: string[][] = [];
    const used = new Set<number>();
    for (let i = 0; i < strs.length; i++) {
        if (used.has(i)) continue;
        const group = [strs[i]];
        for (let j = i + 1; j < strs.length; j++) {
            // Check if anagram - missing implementation
        }
        result.push(group);
    }
    return result;
}`,
    status: 'Wrong Answer',
    runtime: 120,
    memory: 18.5,
    created_at: daysAgo(1),
  },
];

// ============ ALEX (Balanced) ============
// Mixed success across different tags
export const ALEX_SUBMISSIONS: Prisma.SubmissionCreateInput[] = [
  // Array - AC
  {
    id: 'rec-alex-001',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.TWO_SUM_II) } },
    user: { connect: { id: USER_IDS.ALEX } },
    language: 'JavaScript',
    code: `var twoSum = function(numbers, target) {
    let left = 0, right = numbers.length - 1;
    while (left < right) {
        const sum = numbers[left] + numbers[right];
        if (sum === target) return [left + 1, right + 1];
        if (sum < target) left++;
        else right--;
    }
};`,
    status: 'Accepted',
    runtime: 42,
    memory: 14.2,
    runtime_percentile: 82.1,
    memory_percentile: 48.3,
    created_at: daysAgo(10),
  },
  // String - AC
  {
    id: 'rec-alex-002',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.VALID_PALINDROME) } },
    user: { connect: { id: USER_IDS.ALEX } },
    language: 'JavaScript',
    code: `var isPalindrome = function(s) {
    const cleaned = s.toLowerCase().replace(/[^a-z0-9]/g, '');
    let left = 0, right = cleaned.length - 1;
    while (left < right) {
        if (cleaned[left] !== cleaned[right]) return false;
        left++; right--;
    }
    return true;
};`,
    status: 'Accepted',
    runtime: 55,
    memory: 18.5,
    runtime_percentile: 75.3,
    memory_percentile: 42.1,
    created_at: daysAgo(9),
  },
  // DP - WA
  {
    id: 'rec-alex-003',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.COIN_CHANGE) } },
    user: { connect: { id: USER_IDS.ALEX } },
    language: 'JavaScript',
    code: `var coinChange = function(coins, amount) {
    const dp = Array(amount + 1).fill(Infinity);
    dp[0] = 0;
    for (let i = 1; i <= amount; i++) {
        for (const coin of coins) {
            if (coin <= i) dp[i] = Math.min(dp[i], dp[i - coin] + 1);
        }
    }
    return dp[amount];
}`,
    status: 'Wrong Answer',
    runtime: 85,
    memory: 22.1,
    created_at: daysAgo(8),
  },
  // DP - AC (retry)
  {
    id: 'rec-alex-004',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.COIN_CHANGE) } },
    user: { connect: { id: USER_IDS.ALEX } },
    language: 'JavaScript',
    code: `var coinChange = function(coins, amount) {
    const dp = Array(amount + 1).fill(Infinity);
    dp[0] = 0;
    for (let i = 1; i <= amount; i++) {
        for (const coin of coins) {
            if (coin <= i) dp[i] = Math.min(dp[i], dp[i - coin] + 1);
        }
    }
    return dp[amount] === Infinity ? -1 : dp[amount];
}`,
    status: 'Accepted',
    runtime: 82,
    memory: 21.8,
    runtime_percentile: 68.2,
    memory_percentile: 52.3,
    created_at: daysAgo(8),
  },
  // Tree - AC
  {
    id: 'rec-alex-005',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.BINARY_TREE_INORDER) } },
    user: { connect: { id: USER_IDS.ALEX } },
    language: 'JavaScript',
    code: `var inorderTraversal = function(root) {
    const result = [];
    const stack = [];
    let curr = root;
    while (curr || stack.length) {
        while (curr) {
            stack.push(curr);
            curr = curr.left;
        }
        curr = stack.pop();
        result.push(curr.val);
        curr = curr.right;
    }
    return result;
};`,
    status: 'Accepted',
    runtime: 48,
    memory: 16.2,
    runtime_percentile: 79.5,
    memory_percentile: 45.8,
    created_at: daysAgo(7),
  },
  // Graph - WA
  {
    id: 'rec-alex-006',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.COURSE_SCHEDULE) } },
    user: { connect: { id: USER_IDS.ALEX } },
    language: 'JavaScript',
    code: `var canFinish = function(numCourses, prerequisites) {
    const graph = new Map();
    for (const [course, prereq] of prerequisites) {
        if (!graph.has(course)) graph.set(course, []);
        graph.get(course).push(prereq);
    }
    // DFS check - missing visited tracking
    return true;
}`,
    status: 'Wrong Answer',
    runtime: 25,
    memory: 14.5,
    created_at: daysAgo(6),
  },
  // Stack - AC
  {
    id: 'rec-alex-007',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.MIN_STACK) } },
    user: { connect: { id: USER_IDS.ALEX } },
    language: 'JavaScript',
    code: `var MinStack = function() {
    this.stack = [];
    this.minStack = [];
};
MinStack.prototype.push = function(val) {
    this.stack.push(val);
    this.minStack.push(Math.min(val, this.minStack.length ? this.minStack[this.minStack.length - 1] : val));
};
MinStack.prototype.pop = function() { this.stack.pop(); this.minStack.pop(); };
MinStack.prototype.top = function() { return this.stack[this.stack.length - 1]; };
MinStack.prototype.getMin = function() { return this.minStack[this.minStack.length - 1]; };`,
    status: 'Accepted',
    runtime: 72,
    memory: 18.5,
    runtime_percentile: 65.2,
    memory_percentile: 55.1,
    created_at: daysAgo(5),
  },
];

// ============ CHEN (Advanced) ============
// Many Medium ACs, attempting Hard
export const CHEN_SUBMISSIONS: Prisma.SubmissionCreateInput[] = [
  // Medium ACs
  {
    id: 'rec-chen-001',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.THREE_SUM) } },
    user: { connect: { id: USER_IDS.CHEN } },
    language: 'TypeScript',
    code: `function threeSum(nums: number[]): number[][] {
    nums.sort((a, b) => a - b);
    const result: number[][] = [];
    for (let i = 0; i < nums.length - 2; i++) {
        if (i > 0 && nums[i] === nums[i - 1]) continue;
        let left = i + 1, right = nums.length - 1;
        while (left < right) {
            const sum = nums[i] + nums[left] + nums[right];
            if (sum === 0) {
                result.push([nums[i], nums[left++], nums[right--]]);
                while (left < right && nums[left] === nums[left - 1]) left++;
            } else if (sum < 0) left++;
            else right--;
        }
    }
    return result;
}`,
    status: 'Accepted',
    runtime: 125,
    memory: 25.8,
    runtime_percentile: 72.5,
    memory_percentile: 48.2,
    created_at: daysAgo(12),
  },
  {
    id: 'rec-chen-002',
    problem: {
      connect: { id: BigInt(REC_PROBLEM_IDS.CONTAINER_WITH_MOST_WATER) },
    },
    user: { connect: { id: USER_IDS.CHEN } },
    language: 'TypeScript',
    code: `function maxArea(height: number[]): number {
    let left = 0, right = height.length - 1, maxWater = 0;
    while (left < right) {
        maxWater = Math.max(maxWater, Math.min(height[left], height[right]) * (right - left));
        if (height[left] < height[right]) left++;
        else right--;
    }
    return maxWater;
}`,
    status: 'Accepted',
    runtime: 58,
    memory: 18.2,
    runtime_percentile: 85.3,
    memory_percentile: 52.1,
    created_at: daysAgo(11),
  },
  {
    id: 'rec-chen-003',
    problem: {
      connect: { id: BigInt(REC_PROBLEM_IDS.LONGEST_COMMON_SUBSEQUENCE) },
    },
    user: { connect: { id: USER_IDS.CHEN } },
    language: 'TypeScript',
    code: `function longestCommonSubsequence(text1: string, text2: string): number {
    const dp = Array(text1.length + 1).fill(null).map(() => Array(text2.length + 1).fill(0));
    for (let i = 1; i <= text1.length; i++) {
        for (let j = 1; j <= text2.length; j++) {
            if (text1[i - 1] === text2[j - 1]) dp[i][j] = dp[i - 1][j - 1] + 1;
            else dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
        }
    }
    return dp[text1.length][text2.length];
}`,
    status: 'Accepted',
    runtime: 85,
    memory: 22.5,
    runtime_percentile: 78.2,
    memory_percentile: 45.8,
    created_at: daysAgo(10),
  },
  {
    id: 'rec-chen-004',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.VALIDATE_BST) } },
    user: { connect: { id: USER_IDS.CHEN } },
    language: 'TypeScript',
    code: `function isValidBST(root: TreeNode | null, min = -Infinity, max = Infinity): boolean {
    if (!root) return true;
    if (root.val <= min || root.val >= max) return false;
    return isValidBST(root.left, min, root.val) && isValidBST(root.right, root.val, max);
}`,
    status: 'Accepted',
    runtime: 52,
    memory: 16.8,
    runtime_percentile: 82.5,
    memory_percentile: 55.2,
    created_at: daysAgo(9),
  },
  {
    id: 'rec-chen-005',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.COURSE_SCHEDULE) } },
    user: { connect: { id: USER_IDS.CHEN } },
    language: 'TypeScript',
    code: `function canFinish(numCourses: number, prerequisites: number[][]): boolean {
    const graph = new Map<number, number[]>();
    const visited = new Set<number>();
    const recStack = new Set<number>();
    for (const [course, prereq] of prerequisites) {
        if (!graph.has(course)) graph.set(course, []);
        graph.get(course)!.push(prereq);
    }
    function hasCycle(node: number): boolean {
        if (recStack.has(node)) return true;
        if (visited.has(node)) return false;
        visited.add(node);
        recStack.add(node);
        for (const neighbor of graph.get(node) || []) {
            if (hasCycle(neighbor)) return true;
        }
        recStack.delete(node);
        return false;
    }
    for (let i = 0; i < numCourses; i++) {
        if (hasCycle(i)) return false;
    }
    return true;
}`,
    status: 'Accepted',
    runtime: 68,
    memory: 19.2,
    runtime_percentile: 75.8,
    memory_percentile: 48.5,
    created_at: daysAgo(8),
  },
  // Hard attempts
  {
    id: 'rec-chen-006',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.EDIT_DISTANCE) } },
    user: { connect: { id: USER_IDS.CHEN } },
    language: 'TypeScript',
    code: `function minDistance(word1: string, word2: string): number {
    const dp = Array(word1.length + 1).fill(null).map(() => Array(word2.length + 1).fill(0));
    for (let i = 0; i <= word1.length; i++) dp[i][0] = i;
    for (let j = 0; j <= word2.length; j++) dp[0][j] = j;
    for (let i = 1; i <= word1.length; i++) {
        for (let j = 1; j <= word2.length; j++) {
            if (word1[i - 1] === word2[j - 1]) dp[i][j] = dp[i - 1][j - 1];
            else dp[i][j] = Math.min(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1;
        }
    }
    return dp[word1.length][word2.length];
}`,
    status: 'Accepted',
    runtime: 92,
    memory: 24.5,
    runtime_percentile: 72.1,
    memory_percentile: 52.3,
    created_at: daysAgo(7),
  },
  {
    id: 'rec-chen-007',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.REGEX_MATCHING) } },
    user: { connect: { id: USER_IDS.CHEN } },
    language: 'TypeScript',
    code: `function isMatch(s: string, p: string): boolean {
    const dp = Array(s.length + 1).fill(null).map(() => Array(p.length + 1).fill(false));
    dp[0][0] = true;
    for (let j = 2; j <= p.length; j += 2) {
        if (p[j - 1] === '*') dp[0][j] = dp[0][j - 2];
    }
    for (let i = 1; i <= s.length; i++) {
        for (let j = 1; j <= p.length; j++) {
            if (p[j - 1] === '.' || p[j - 1] === s[i - 1]) {
                dp[i][j] = dp[i - 1][j - 1];
            } else if (p[j - 1] === '*') {
                dp[i][j] = dp[i][j - 2] || ((p[j - 2] === '.' || p[j - 2] === s[i - 1]) && dp[i - 1][j]);
            }
        }
    }
    return dp[s.length][p.length];
}`,
    status: 'Accepted',
    runtime: 78,
    memory: 21.8,
    runtime_percentile: 68.5,
    memory_percentile: 55.2,
    created_at: daysAgo(6),
  },
];

// ============ MAX (Weak Point - Array Strong, DP Weak) ============
export const MAX_SUBMISSIONS: Prisma.SubmissionCreateInput[] = [
  // Array ACs (Strong)
  {
    id: 'rec-max-001',
    problem: { connect: { id: BigInt(PROBLEM_IDS.TWO_SUM) } },
    user: { connect: { id: USER_IDS.MAX } },
    language: 'JavaScript',
    code: `var twoSum = function(nums, target) {
    const map = new Map();
    for (let i = 0; i < nums.length; i++) {
        const diff = target - nums[i];
        if (map.has(diff)) return [map.get(diff), i];
        map.set(nums[i], i);
    }
};`,
    status: 'Accepted',
    runtime: 45,
    memory: 14.2,
    runtime_percentile: 82.5,
    memory_percentile: 48.2,
    created_at: daysAgo(14),
  },
  {
    id: 'rec-max-002',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.TWO_SUM_II) } },
    user: { connect: { id: USER_IDS.MAX } },
    language: 'JavaScript',
    code: `var twoSum = function(numbers, target) {
    let left = 0, right = numbers.length - 1;
    while (left < right) {
        const sum = numbers[left] + numbers[right];
        if (sum === target) return [left + 1, right + 1];
        sum < target ? left++ : right--;
    }
};`,
    status: 'Accepted',
    runtime: 38,
    memory: 13.8,
    runtime_percentile: 88.2,
    memory_percentile: 52.1,
    created_at: daysAgo(13),
  },
  {
    id: 'rec-max-003',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.THREE_SUM) } },
    user: { connect: { id: USER_IDS.MAX } },
    language: 'JavaScript',
    code: `var threeSum = function(nums) {
    nums.sort((a, b) => a - b);
    const result = [];
    for (let i = 0; i < nums.length - 2; i++) {
        if (i > 0 && nums[i] === nums[i - 1]) continue;
        let left = i + 1, right = nums.length - 1;
        while (left < right) {
            const sum = nums[i] + nums[left] + nums[right];
            if (sum === 0) {
                result.push([nums[i], nums[left++], nums[right--]]);
                while (left < right && nums[left] === nums[left - 1]) left++;
            } else sum < 0 ? left++ : right--;
        }
    }
    return result;
};`,
    status: 'Accepted',
    runtime: 118,
    memory: 24.5,
    runtime_percentile: 75.2,
    memory_percentile: 52.1,
    created_at: daysAgo(12),
  },
  {
    id: 'rec-max-004',
    problem: {
      connect: { id: BigInt(REC_PROBLEM_IDS.CONTAINER_WITH_MOST_WATER) },
    },
    user: { connect: { id: USER_IDS.MAX } },
    language: 'JavaScript',
    code: `var maxArea = function(height) {
    let left = 0, right = height.length - 1, maxWater = 0;
    while (left < right) {
        maxWater = Math.max(maxWater, Math.min(height[left], height[right]) * (right - left));
        height[left] < height[right] ? left++ : right--;
    }
    return maxWater;
};`,
    status: 'Accepted',
    runtime: 55,
    memory: 17.8,
    runtime_percentile: 86.2,
    memory_percentile: 55.3,
    created_at: daysAgo(11),
  },
  {
    id: 'rec-max-005',
    problem: {
      connect: { id: BigInt(REC_PROBLEM_IDS.PRODUCT_OF_ARRAY_EXCEPT_SELF) },
    },
    user: { connect: { id: USER_IDS.MAX } },
    language: 'JavaScript',
    code: `var productExceptSelf = function(nums) {
    const result = Array(nums.length).fill(1);
    let prefix = 1, suffix = 1;
    for (let i = 0; i < nums.length; i++) {
        result[i] *= prefix;
        prefix *= nums[i];
    }
    for (let i = nums.length - 1; i >= 0; i--) {
        result[i] *= suffix;
        suffix *= nums[i];
    }
    return result;
};`,
    status: 'Accepted',
    runtime: 72,
    memory: 19.5,
    runtime_percentile: 78.5,
    memory_percentile: 62.1,
    created_at: daysAgo(10),
  },
  // DP Failures (Weak)
  {
    id: 'rec-max-006',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.CLIMBING_STAIRS) } },
    user: { connect: { id: USER_IDS.MAX } },
    language: 'JavaScript',
    code: `var climbStairs = function(n) {
    // Don't understand the pattern
    return n * 2;
}`,
    status: 'Wrong Answer',
    runtime: 35,
    memory: 10.2,
    created_at: daysAgo(9),
  },
  {
    id: 'rec-max-007',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.CLIMBING_STAIRS) } },
    user: { connect: { id: USER_IDS.MAX } },
    language: 'JavaScript',
    code: `var climbStairs = function(n) {
    // Try again
    if (n <= 2) return n;
    return climbStairs(n - 1) + climbStairs(n - 2);
}`,
    status: 'Time Limit Exceeded',
    runtime: 5000,
    memory: 15.8,
    created_at: daysAgo(9),
  },
  {
    id: 'rec-max-008',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.COIN_CHANGE) } },
    user: { connect: { id: USER_IDS.MAX } },
    language: 'JavaScript',
    code: `var coinChange = function(coins, amount) {
    // Greedy approach - doesn't work
    coins.sort((a, b) => b - a);
    let count = 0;
    for (const coin of coins) {
        count += Math.floor(amount / coin);
        amount %= coin;
    }
    return amount === 0 ? count : -1;
}`,
    status: 'Wrong Answer',
    runtime: 45,
    memory: 14.2,
    created_at: daysAgo(8),
  },
  {
    id: 'rec-max-009',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.EDIT_DISTANCE) } },
    user: { connect: { id: USER_IDS.MAX } },
    language: 'JavaScript',
    code: `var minDistance = function(word1, word2) {
    // Not sure how to approach this
    return Math.abs(word1.length - word2.length);
}`,
    status: 'Wrong Answer',
    runtime: 42,
    memory: 12.5,
    created_at: daysAgo(7),
  },
];

// ============ SARA (Biased - String Strong, Tree Weak) ============
export const SARA_SUBMISSIONS: Prisma.SubmissionCreateInput[] = [
  // String ACs (Strong)
  {
    id: 'rec-sara-001',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.VALID_ANAGRAM) } },
    user: { connect: { id: USER_IDS.SARA } },
    language: 'TypeScript',
    code: `function isAnagram(s: string, t: string): boolean {
    return s.split('').sort().join('') === t.split('').sort().join('');
}`,
    status: 'Accepted',
    runtime: 85,
    memory: 18.5,
    runtime_percentile: 55.2,
    memory_percentile: 42.1,
    created_at: daysAgo(10),
  },
  {
    id: 'rec-sara-002',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.GROUP_ANAGRAMS) } },
    user: { connect: { id: USER_IDS.SARA } },
    language: 'TypeScript',
    code: `function groupAnagrams(strs: string[]): string[][] {
    const map = new Map<string, string[]>();
    for (const str of strs) {
        const key = str.split('').sort().join('');
        if (!map.has(key)) map.set(key, []);
        map.get(key)!.push(str);
    }
    return Array.from(map.values());
}`,
    status: 'Accepted',
    runtime: 95,
    memory: 28.5,
    runtime_percentile: 68.5,
    memory_percentile: 52.3,
    created_at: daysAgo(9),
  },
  {
    id: 'rec-sara-003',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.VALID_PALINDROME) } },
    user: { connect: { id: USER_IDS.SARA } },
    language: 'TypeScript',
    code: `function isPalindrome(s: string): boolean {
    const cleaned = s.toLowerCase().replace(/[^a-z0-9]/g, '');
    return cleaned === cleaned.split('').reverse().join('');
}`,
    status: 'Accepted',
    runtime: 62,
    memory: 20.5,
    runtime_percentile: 72.1,
    memory_percentile: 38.5,
    created_at: daysAgo(8),
  },
  {
    id: 'rec-sara-004',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.LONGEST_PALINDROME) } },
    user: { connect: { id: USER_IDS.SARA } },
    language: 'TypeScript',
    code: `function longestPalindrome(s: string): string {
    let result = '';
    for (let i = 0; i < s.length; i++) {
        const odd = expand(s, i, i);
        const even = expand(s, i, i + 1);
        result = result.length > odd.length ? result : odd;
        result = result.length > even.length ? result : even;
    }
    return result;
}
function expand(s: string, left: number, right: number): string {
    while (left >= 0 && right < s.length && s[left] === s[right]) {
        left--; right++;
    }
    return s.slice(left + 1, right);
}`,
    status: 'Accepted',
    runtime: 88,
    memory: 22.5,
    runtime_percentile: 75.2,
    memory_percentile: 48.5,
    created_at: daysAgo(7),
  },
  // Tree Failures (Weak)
  {
    id: 'rec-sara-005',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.BINARY_TREE_INORDER) } },
    user: { connect: { id: USER_IDS.SARA } },
    language: 'TypeScript',
    code: `function inorderTraversal(root: TreeNode | null): number[] {
    // Don't understand tree traversal
    return root ? [root.val] : [];
}`,
    status: 'Wrong Answer',
    runtime: 42,
    memory: 12.5,
    created_at: daysAgo(6),
  },
  {
    id: 'rec-sara-006',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.MAX_DEPTH_TREE) } },
    user: { connect: { id: USER_IDS.SARA } },
    language: 'TypeScript',
    code: `function maxDepth(root: TreeNode | null): number {
    // Guess
    return root ? 1 : 0;
}`,
    status: 'Wrong Answer',
    runtime: 38,
    memory: 11.2,
    created_at: daysAgo(5),
  },
  {
    id: 'rec-sara-007',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.VALIDATE_BST) } },
    user: { connect: { id: USER_IDS.SARA } },
    language: 'TypeScript',
    code: `function isValidBST(root: TreeNode | null): boolean {
    // Not sure about BST properties
    if (!root) return true;
    const leftValid = !root.left || root.left.val < root.val;
    const rightValid = !root.right || root.right.val > root.val;
    return leftValid && rightValid;
}`,
    status: 'Wrong Answer',
    runtime: 45,
    memory: 14.2,
    created_at: daysAgo(4),
  },
];

// ============ LILY (Challenger) ============
// Many Medium ACs, ready for Hard challenges
export const LILY_SUBMISSIONS: Prisma.SubmissionCreateInput[] = [
  // Medium ACs
  {
    id: 'rec-lily-001',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.THREE_SUM_CLOSEST) } },
    user: { connect: { id: USER_IDS.LILY } },
    language: 'TypeScript',
    code: `function threeSumClosest(nums: number[], target: number): number {
    nums.sort((a, b) => a - b);
    let closest = nums[0] + nums[1] + nums[2];
    for (let i = 0; i < nums.length - 2; i++) {
        let left = i + 1, right = nums.length - 1;
        while (left < right) {
            const sum = nums[i] + nums[left] + nums[right];
            if (Math.abs(sum - target) < Math.abs(closest - target)) closest = sum;
            if (sum < target) left++;
            else if (sum > target) right--;
            else return target;
        }
    }
    return closest;
}`,
    status: 'Accepted',
    runtime: 72,
    memory: 18.5,
    runtime_percentile: 78.5,
    memory_percentile: 52.1,
    created_at: daysAgo(8),
  },
  {
    id: 'rec-lily-002',
    problem: {
      connect: { id: BigInt(REC_PROBLEM_IDS.LONGEST_COMMON_SUBSEQUENCE) },
    },
    user: { connect: { id: USER_IDS.LILY } },
    language: 'TypeScript',
    code: `function longestCommonSubsequence(text1: string, text2: string): number {
    const dp = Array(text1.length + 1).fill(null).map(() => Array(text2.length + 1).fill(0));
    for (let i = 1; i <= text1.length; i++) {
        for (let j = 1; j <= text2.length; j++) {
            if (text1[i - 1] === text2[j - 1]) dp[i][j] = dp[i - 1][j - 1] + 1;
            else dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
        }
    }
    return dp[text1.length][text2.length];
}`,
    status: 'Accepted',
    runtime: 82,
    memory: 22.5,
    runtime_percentile: 75.2,
    memory_percentile: 48.5,
    created_at: daysAgo(7),
  },
  {
    id: 'rec-lily-003',
    problem: {
      connect: { id: BigInt(REC_PROBLEM_IDS.BINARY_TREE_LEVEL_ORDER) },
    },
    user: { connect: { id: USER_IDS.LILY } },
    language: 'TypeScript',
    code: `function levelOrder(root: TreeNode | null): number[][] {
    if (!root) return [];
    const result: number[][] = [];
    const queue: TreeNode[] = [root];
    while (queue.length) {
        const level: number[] = [];
        const size = queue.length;
        for (let i = 0; i < size; i++) {
            const node = queue.shift()!;
            level.push(node.val);
            if (node.left) queue.push(node.left);
            if (node.right) queue.push(node.right);
        }
        result.push(level);
    }
    return result;
}`,
    status: 'Accepted',
    runtime: 55,
    memory: 17.2,
    runtime_percentile: 82.5,
    memory_percentile: 55.1,
    created_at: daysAgo(6),
  },
  {
    id: 'rec-lily-004',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.CLONE_GRAPH) } },
    user: { connect: { id: USER_IDS.LILY } },
    language: 'TypeScript',
    code: `function cloneGraph(node: Node | null): Node | null {
    if (!node) return null;
    const map = new Map<Node, Node>();
    function dfs(n: Node): Node {
        if (map.has(n)) return map.get(n)!;
        const clone = new Node(n.val);
        map.set(n, clone);
        for (const neighbor of n.neighbors) {
            clone.neighbors.push(dfs(neighbor));
        }
        return clone;
    }
    return dfs(node);
}`,
    status: 'Accepted',
    runtime: 62,
    memory: 18.5,
    runtime_percentile: 75.8,
    memory_percentile: 52.2,
    created_at: daysAgo(5),
  },
  {
    id: 'rec-lily-005',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.TOP_K_FREQUENT) } },
    user: { connect: { id: USER_IDS.LILY } },
    language: 'TypeScript',
    code: `function topKFrequent(nums: number[], k: number): number[] {
    const count = new Map<number, number>();
    for (const n of nums) count.set(n, (count.get(n) || 0) + 1);
    const buckets: number[][] = Array(nums.length + 1).fill(null).map(() => []);
    for (const [num, freq] of count) buckets[freq].push(num);
    const result: number[] = [];
    for (let i = buckets.length - 1; i >= 0 && result.length < k; i--) {
        result.push(...buckets[i]);
    }
    return result.slice(0, k);
}`,
    status: 'Accepted',
    runtime: 68,
    memory: 20.5,
    runtime_percentile: 78.2,
    memory_percentile: 48.5,
    created_at: daysAgo(4),
  },
  // Ready for Hard - no Hard submissions yet
];

// ============ DAVID (All-rounder) ============
// AC records across all tags
export const DAVID_SUBMISSIONS: Prisma.SubmissionCreateInput[] = [
  // Array
  {
    id: 'rec-david-001',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.SEARCH_ROTATED) } },
    user: { connect: { id: USER_IDS.DAVID } },
    language: 'TypeScript',
    code: `function search(nums: number[], target: number): number {
    let left = 0, right = nums.length - 1;
    while (left <= right) {
        const mid = Math.floor((left + right) / 2);
        if (nums[mid] === target) return mid;
        if (nums[left] <= nums[mid]) {
            if (nums[left] <= target && target < nums[mid]) right = mid - 1;
            else left = mid + 1;
        } else {
            if (nums[mid] < target && target <= nums[right]) left = mid + 1;
            else right = mid - 1;
        }
    }
    return -1;
}`,
    status: 'Accepted',
    runtime: 48,
    memory: 14.5,
    runtime_percentile: 85.2,
    memory_percentile: 55.1,
    created_at: daysAgo(10),
  },
  // String
  {
    id: 'rec-david-002',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.GROUP_ANAGRAMS) } },
    user: { connect: { id: USER_IDS.DAVID } },
    language: 'TypeScript',
    code: `function groupAnagrams(strs: string[]): string[][] {
    const map = new Map<string, string[]>();
    for (const str of strs) {
        const count = Array(26).fill(0);
        for (const c of str) count[c.charCodeAt(0) - 97]++;
        const key = count.join('#');
        if (!map.has(key)) map.set(key, []);
        map.get(key)!.push(str);
    }
    return Array.from(map.values());
}`,
    status: 'Accepted',
    runtime: 78,
    memory: 25.5,
    runtime_percentile: 82.1,
    memory_percentile: 48.2,
    created_at: daysAgo(9),
  },
  // DP
  {
    id: 'rec-david-003',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.MAX_SUBARRAY) } },
    user: { connect: { id: USER_IDS.DAVID } },
    language: 'TypeScript',
    code: `function maxSubArray(nums: number[]): number {
    let maxSum = nums[0], currentSum = nums[0];
    for (let i = 1; i < nums.length; i++) {
        currentSum = Math.max(nums[i], currentSum + nums[i]);
        maxSum = Math.max(maxSum, currentSum);
    }
    return maxSum;
}`,
    status: 'Accepted',
    runtime: 55,
    memory: 15.2,
    runtime_percentile: 88.5,
    memory_percentile: 62.1,
    created_at: daysAgo(8),
  },
  // Tree
  {
    id: 'rec-david-004',
    problem: {
      connect: { id: BigInt(REC_PROBLEM_IDS.BINARY_TREE_LEVEL_ORDER) },
    },
    user: { connect: { id: USER_IDS.DAVID } },
    language: 'TypeScript',
    code: `function levelOrder(root: TreeNode | null): number[][] {
    if (!root) return [];
    const result: number[][] = [];
    const queue: TreeNode[] = [root];
    while (queue.length) {
        const level: number[] = [];
        const size = queue.length;
        for (let i = 0; i < size; i++) {
            const node = queue.shift()!;
            level.push(node.val);
            if (node.left) queue.push(node.left);
            if (node.right) queue.push(node.right);
        }
        result.push(level);
    }
    return result;
}`,
    status: 'Accepted',
    runtime: 52,
    memory: 16.8,
    runtime_percentile: 85.2,
    memory_percentile: 55.1,
    created_at: daysAgo(7),
  },
  // Graph
  {
    id: 'rec-david-005',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.PACIFIC_ATLANTIC) } },
    user: { connect: { id: USER_IDS.DAVID } },
    language: 'TypeScript',
    code: `function pacificAtlantic(heights: number[][]): number[][] {
    const m = heights.length, n = heights[0].length;
    const pacific = Array(m).fill(null).map(() => Array(n).fill(false));
    const atlantic = Array(m).fill(null).map(() => Array(n).fill(false));
    const dirs = [[0, 1], [0, -1], [1, 0], [-1, 0]];

    function dfs(r: number, c: number, visited: boolean[][], prevHeight: number) {
        if (r < 0 || c < 0 || r >= m || c >= n || visited[r][c] || heights[r][c] < prevHeight) return;
        visited[r][c] = true;
        for (const [dr, dc] of dirs) dfs(r + dr, c + dc, visited, heights[r][c]);
    }

    for (let i = 0; i < m; i++) {
        dfs(i, 0, pacific, 0);
        dfs(i, n - 1, atlantic, 0);
    }
    for (let j = 0; j < n; j++) {
        dfs(0, j, pacific, 0);
        dfs(m - 1, j, atlantic, 0);
    }

    const result: number[][] = [];
    for (let i = 0; i < m; i++) {
        for (let j = 0; j < n; j++) {
            if (pacific[i][j] && atlantic[i][j]) result.push([i, j]);
        }
    }
    return result;
}`,
    status: 'Accepted',
    runtime: 95,
    memory: 22.5,
    runtime_percentile: 72.5,
    memory_percentile: 48.2,
    created_at: daysAgo(6),
  },
  // Stack
  {
    id: 'rec-david-006',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.EVALUATE_RPN) } },
    user: { connect: { id: USER_IDS.DAVID } },
    language: 'TypeScript',
    code: `function evalRPN(tokens: string[]): number {
    const stack: number[] = [];
    const ops: Record<string, (a: number, b: number) => number> = {
        '+': (a, b) => a + b,
        '-': (a, b) => a - b,
        '*': (a, b) => a * b,
        '/': (a, b) => Math.trunc(a / b),
    };
    for (const token of tokens) {
        if (token in ops) {
            const b = stack.pop()!, a = stack.pop()!;
            stack.push(ops[token](a, b));
        } else {
            stack.push(parseInt(token));
        }
    }
    return stack[0];
}`,
    status: 'Accepted',
    runtime: 58,
    memory: 15.8,
    runtime_percentile: 82.1,
    memory_percentile: 55.2,
    created_at: daysAgo(5),
  },
  // Linked List
  {
    id: 'rec-david-007',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.LRU_CACHE) } },
    user: { connect: { id: USER_IDS.DAVID } },
    language: 'TypeScript',
    code: `class LRUCache {
    private capacity: number;
    private map = new Map<number, ListNode>();
    private head = new ListNode(0, 0);
    private tail = new ListNode(0, 0);

    constructor(capacity: number) {
        this.capacity = capacity;
        this.head.next = this.tail;
        this.tail.prev = this.head;
    }

    get(key: number): number {
        const node = this.map.get(key);
        if (!node) return -1;
        this.remove(node);
        this.add(node);
        return node.val;
    }

    put(key: number, value: number): void {
        if (this.map.has(key)) {
            this.remove(this.map.get(key)!);
        }
        const node = new ListNode(key, value);
        this.add(node);
        this.map.set(key, node);
        if (this.map.size > this.capacity) {
            const lru = this.tail.prev;
            this.remove(lru);
            this.map.delete(lru.key);
        }
    }

    private add(node: ListNode): void {
        node.next = this.head.next;
        node.prev = this.head;
        this.head.next.prev = node;
        this.head.next = node;
    }

    private remove(node: ListNode): void {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
}`,
    status: 'Accepted',
    runtime: 185,
    memory: 28.5,
    runtime_percentile: 75.2,
    memory_percentile: 52.1,
    created_at: daysAgo(4),
  },
  // Hard
  {
    id: 'rec-david-008',
    problem: { connect: { id: BigInt(REC_PROBLEM_IDS.TRAPPING_RAIN_WATER) } },
    user: { connect: { id: USER_IDS.DAVID } },
    language: 'TypeScript',
    code: `function trap(height: number[]): number {
    let left = 0, right = height.length - 1;
    let leftMax = 0, rightMax = 0, water = 0;
    while (left < right) {
        if (height[left] < height[right]) {
            if (height[left] >= leftMax) leftMax = height[left];
            else water += leftMax - height[left];
            left++;
        } else {
            if (height[right] >= rightMax) rightMax = height[right];
            else water += rightMax - height[right];
            right--;
        }
    }
    return water;
}`,
    status: 'Accepted',
    runtime: 52,
    memory: 16.2,
    runtime_percentile: 88.5,
    memory_percentile: 62.1,
    created_at: daysAgo(3),
  },
];

// Combine all submissions
export const REC_SUBMISSIONS: Prisma.SubmissionCreateInput[] = [
  ...YUKI_SUBMISSIONS,
  ...ALEX_SUBMISSIONS,
  ...CHEN_SUBMISSIONS,
  ...MAX_SUBMISSIONS,
  ...SARA_SUBMISSIONS,
  ...LILY_SUBMISSIONS,
  ...DAVID_SUBMISSIONS,
];

export default REC_SUBMISSIONS;
