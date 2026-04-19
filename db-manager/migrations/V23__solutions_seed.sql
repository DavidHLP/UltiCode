SET FOREIGN_KEY_CHECKS=0;
START TRANSACTION;
-- V23__solutions_seed.sql: ~92 new solutions
-- Covers 27 previously uncovered problems + additional solutions for existing 5
-- Total: ~100 solutions across 32 problems

INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-009',1,'user-yuki',N'双指针夹逼法',N'## 题目理解

两数之和的双指针夹逼解法。

## 解题思路

先排序，然后用左右指针向中间逼近：
- 如果和大于目标，右指针左移
- 如果和小于目标，左指针右移

## 方法

```typescript
function twoSum(nums: number[], target: number): number[] {
    const sorted = nums.map((v, i) => [v, i]).sort((a, b) => a[0] - b[0]);
    let left = 0, right = sorted.length - 1;
    
    while (left < right) {
        const sum = sorted[left][0] + sorted[right][0];
        if (sum === target) {
            return [sorted[left][1], sorted[right][1]];
        } else if (sum < target) {
            left++;
        } else {
            right--;
        }
    }
    
    return [];
}
```

## 复杂度分析
- 时间复杂度：O(n log n)
- 空间复杂度：O(n)',N'双指针 O(n log n)','typescript','["two-pointers","array"]',213,NOW(3),NOW(3),1,NOW(3),'user-yuki',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-010',1,'user-alex',N'数学公式法',N'## 题目理解

利用数学公式 target - nums[i] 求解。

## 解题思路

对于每个数，检查 target - num 是否存在。

```python
def two_sum(nums, target):
    seen = {}
    for i, num in enumerate(nums):
        complement = target - num
        if complement in seen:
            return [seen[complement], i]
        seen[num] = i
    return []
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(n)',N'哈希表 O(n)','python','["math"]',78,NOW(3),NOW(3),1,NOW(3),'user-alex',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-011',1,'user-chen',N'两遍哈希',N'## 题目理解

两遍哈希表：第一遍建立映射，第二遍查找。

## 方法

```typescript
function twoSumHash(nums: number[], target: number): number[] {
    const map = new Map<number, number>();
    
    // 第一遍：建立映射
    for (let i = 0; i < nums.length; i++) {
        map.set(nums[i], i);
    }
    
    // 第二遍：查找
    for (let i = 0; i < nums.length; i++) {
        const complement = target - nums[i];
        if (map.has(complement) && map.get(complement) !== i) {
            return [i, map.get(complement)!];
        }
    }
    
    return [];
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(n)',N'两遍哈希','typescript','["hash-map"]',56,NOW(3),NOW(3),1,NOW(3),'user-chen',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-012',2,'user-tourist',N'Set 去重法',N'## 题目理解

找出最长无重复字符的子串。

## 解题思路

使用 Set 维护滑动窗口中的字符。

## 方法

```typescript
function lengthOfLongestSubstring(s: string): number {
    const set = new Set<string>();
    let left = 0, maxLen = 0;
    
    for (let right = 0; right < s.length; right++) {
        while (set.has(s[right])) {
            set.delete(s[left]);
            left++;
        }
        set.add(s[right]);
        maxLen = Math.max(maxLen, right - left + 1);
    }
    
    return maxLen;
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(min(m, n))',N'Set 滑动窗口','typescript','["sliding-window","set"]',239,NOW(3),NOW(3),1,NOW(3),'user-tourist',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-013',2,'user-sara',N'HashMap 记录位置',N'## 题目理解

用 HashMap 记录字符最新位置。

## 方法

```python
def lengthOfLongestSubstring(s: str) -> int:
    last_seen = {}
    left = 0
    max_len = 0
    
    for right, char in enumerate(s):
        if char in last_seen and last_seen[char] >= left:
            left = last_seen[char] + 1
        last_seen[char] = right
        max_len = max(max_len, right - left + 1)
    
    return max_len
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(min(m, n))',N'HashMap 优化','python','["sliding-window","hash-map"]',120,NOW(3),NOW(3),1,NOW(3),'user-sara',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-014',3,'user-max',N'排序合并',N'## 题目理解

合并重叠的区间。

## 解题思路

按起点排序，然后逐个合并。

## 方法

```typescript
function merge(intervals: number[][]): number[][] {
    if (intervals.length <= 1) return intervals;
    
    intervals.sort((a, b) => a[0] - b[0]);
    const result = [intervals[0]];
    
    for (let i = 1; i < intervals.length; i++) {
        const last = result[result.length - 1];
        const curr = intervals[i];
        
        if (curr[0] <= last[1]) {
            last[1] = Math.max(last[1], curr[1]);
        } else {
            result.push(curr);
        }
    }
    
    return result;
}
```

## 复杂度分析
- 时间复杂度：O(n log n)
- 空间复杂度：O(log n)',N'排序合并法','typescript','["sorting","intervals"]',112,NOW(3),NOW(3),1,NOW(3),'user-max',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-015',3,'user-petr',N'栈方法',N'## 题目理解

使用栈来合并区间。

## 方法

```python
def merge(intervals):
    intervals.sort()
    stack = [intervals[0]]
    
    for start, end in intervals[1:]:
        if stack[-1][1] >= start:
            stack[-1][1] = max(stack[-1][1], end)
        else:
            stack.append([start, end])
    
    return stack
```

## 复杂度分析
- 时间复杂度：O(n log n)
- 空间复杂度：O(n)',N'栈方法','python','["stack","intervals"]',107,NOW(3),NOW(3),1,NOW(3),'user-petr',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-016',4,'user-emma',N'二分查找',N'## 题目理解

找两个有序数组的中位数。

## 解题思路

二分查找第一个数组的分割位置，使左半部分和右半部分元素数量相等且左半最大值 <= 右半最小值。

## 方法

```typescript
function findMedianSortedArrays(nums1: number[], nums2: number[]): number {
    if (nums1.length > nums2.length) {
        [nums1, nums2] = [nums2, nums1];
    }
    
    const m = nums1.length, n = nums2.length;
    let left = 0, right = m;
    
    while (left <= right) {
        const partitionA = Math.floor((left + right) / 2);
        const partitionB = Math.floor((m + n + 1) / 2) - partitionA;
        
        const maxA = partitionA === 0 ? -Infinity : nums1[partitionA - 1];
        const minA = partitionA === m ? Infinity : nums1[partitionA];
        const maxB = partitionB === 0 ? -Infinity : nums2[partitionB - 1];
        const minB = partitionB === n ? Infinity : nums2[partitionB];
        
        if (maxA <= minB && maxB <= minA) {
            const maxLeft = Math.max(maxA, maxB);
            const minRight = Math.min(minA, minB);
            return (m + n) % 2 === 0 
                ? (maxLeft + minRight) / 2 
                : maxLeft;
        } else if (maxA > minB) {
            right = partitionA - 1;
        } else {
            left = partitionA + 1;
        }
    }
    
    return 0;
}
```

## 复杂度分析
- 时间复杂度：O(log(min(m,n)))
- 空间复杂度：O(1)',N'二分查找最优解','typescript','["binary-search","divide-and-conquer"]',85,NOW(3),NOW(3),1,NOW(3),'user-emma',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-017',5,'user-lily',N'BFS 网格遍历',N'## 题目理解

计算网格中岛屿的数量。

## 解题思路

BFS 或 DFS 遍历所有格子，遇到 1 就从它开始 BFS/DFS 把所有相连的 1 都标记为已访问。

## 方法

```typescript
function numIslands(grid: string[][]): number {
    if (!grid.length) return 0;
    
    const m = grid.length, n = grid[0].length;
    let count = 0;
    
    function bfs(i: number, j: number) {
        const queue: [number, number][] = [[i, j]];
        grid[i][j] = '0';
        
        while (queue.length) {
            const [r, c] = queue.shift()!;
            const dirs = [[0,1],[0,-1],[1,0],[-1,0]];
            
            for (const [dr, dc] of dirs) {
                const nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < m && nc >= 0 && nc < n && grid[nr][nc] === '1') {
                    grid[nr][nc] = '0';
                    queue.push([nr, nc]);
                }
            }
        }
    }
    
    for (let i = 0; i < m; i++) {
        for (let j = 0; j < n; j++) {
            if (grid[i][j] === '1') {
                count++;
                bfs(i, j);
            }
        }
    }
    
    return count;
}
```

## 复杂度分析
- 时间复杂度：O(m*n)
- 空间复杂度：O(m*n)',N'BFS 网格遍历','typescript','["bfs","graph","grid"]',238,NOW(3),NOW(3),1,NOW(3),'user-lily',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-018',6,'user-scott',N'SQL JOIN 解法',N'## 题目理解

本题要求合并两个表的数据。需要注意当没有匹配时也要显示。

## 解题思路

使用 SQL JOIN 语法：
- LEFT JOIN 保留左表所有记录
- RIGHT JOIN 保留右表所有记录
- FULL OUTER JOIN 保留两边

## 方法一：LEFT JOIN

```sql
SELECT p.FirstName, p.LastName, a.City, a.State
FROM Person p
LEFT JOIN Address a ON p.PersonId = a.PersonId;
```

## 方法二：RIGHT JOIN

```sql
SELECT p.FirstName, p.LastName, a.City, a.State
FROM Address a
RIGHT JOIN Person p ON a.PersonId = p.PersonId;
```

## 复杂度分析
- 时间复杂度：O(M+N)，M 和 N 分别是两个表的行数
- 空间复杂度：O(1)，仅使用常数额外空间',N'使用 LEFT JOIN 合并表数据','typescript','["sql","join"]',82,NOW(3),NOW(3),1,NOW(3),'user-scott',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-019',6,'user-tom',N'问题 6 解法',N'## 问题 6 解法

使用标准算法思路解决。',N'问题 6 的解法','typescript','["algorithm"]',376,NOW(3),NOW(3),1,NOW(3),'user-tom',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-020',6,'user-david',N'问题 6 解法',N'## 问题 6 解法

使用标准算法思路解决。',N'问题 6 的解法','typescript','["algorithm"]',309,NOW(3),NOW(3),1,NOW(3),'user-david',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-021',6,'user-kevin',N'问题 6 解法',N'## 问题 6 解法

使用标准算法思路解决。',N'问题 6 的解法','typescript','["algorithm"]',74,NOW(3),NOW(3),1,NOW(3),'user-kevin',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-022',7,'user-benq',N'LIMIT OFFSET 解法',N'## 题目理解

找出文件中第 10 行内容。

## 解题思路

使用以下方法：
1. head + tail 组合
2. sed 行号提取
3. awk NR 处理

## 方法一：head + tail

```bash
head -n 10 file.txt | tail -n 1
```

## 方法二：sed

```bash
sed -n '10p' file.txt
```

## 方法三：awk

```bash
awk 'NR==10' file.txt
```

## 复杂度分析
- 时间复杂度：O(n)，需要读取前 n 行
- 空间复杂度：O(1)',N'使用 sed 提取第 10 行','bash','["bash","sql"]',332,NOW(3),NOW(3),1,NOW(3),'user-benq',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-023',7,'user-ecnerwala',N'NR==10 解法',N'## 题目理解

使用 awk 提取第 10 行。

## 解题思路

awk 的 NR 变量表示当前行号，当 NR 等于 10 时打印该行。

```bash
awk 'NR==10 {print; exit}' file.txt
```

## 复杂度分析
- 时间复杂度：O(n)，最坏情况需读取 n 行
- 空间复杂度：O(1)',N'awk 简洁方案','bash','["awk","bash"]',246,NOW(3),NOW(3),1,NOW(3),'user-ecnerwala',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-024',7,'user-jiangly',N'问题 7 解法',N'## 问题 7 解法

使用标准算法思路解决。',N'问题 7 的解法','typescript','["algorithm"]',46,NOW(3),NOW(3),1,NOW(3),'user-jiangly',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-025',8,'user-um',N'问题 8 解法',N'## 问题 8 解法

使用标准算法思路解决。',N'问题 8 的解法','typescript','["algorithm"]',45,NOW(3),NOW(3),1,NOW(3),'user-um',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-026',8,'user-yuki',N'问题 8 解法',N'## 问题 8 解法

使用标准算法思路解决。',N'问题 8 的解法','typescript','["algorithm"]',77,NOW(3),NOW(3),1,NOW(3),'user-yuki',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-027',9,'user-alex',N'Kadane 算法',N'## 题目理解

找到连续子数组的最大和。

## 解题思路

使用 Kadane 算法：
- 遍历数组，维护当前最大和
- 如果当前和为负数，则重新开始

## 方法一：Kadane 算法

```typescript
function maxSubArray(nums: number[]): number {
    let maxSum = nums[0];
    let currentSum = nums[0];
    
    for (let i = 1; i < nums.length; i++) {
        // 如果当前和为负数，丢弃之前的累加
        currentSum = Math.max(nums[i], currentSum + nums[i]);
        maxSum = Math.max(maxSum, currentSum);
    }
    
    return maxSum;
}
```

## 方法二：动态规划

```typescript
function maxSubArrayDP(nums: number[]): number {
    const n = nums.length;
    const dp = new Array(n);
    dp[0] = nums[0];
    
    for (let i = 1; i < n; i++) {
        dp[i] = Math.max(nums[i], dp[i-1] + nums[i]);
    }
    
    return Math.max(...dp);
}
```

## 复杂度分析
- 时间复杂度：O(n)，单次遍历
- 空间复杂度：O(1) 或 O(n)',N'Kadane 算法最优解','typescript','["dynamic-programming","kadane"]',141,NOW(3),NOW(3),1,NOW(3),'user-alex',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-028',9,'user-chen',N'问题 9 解法',N'## 问题 9 解法

使用标准算法思路解决。',N'问题 9 的解法','typescript','["algorithm"]',149,NOW(3),NOW(3),1,NOW(3),'user-chen',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-029',9,'user-tourist',N'问题 9 解法',N'## 问题 9 解法

使用标准算法思路解决。',N'问题 9 的解法','typescript','["algorithm"]',288,NOW(3),NOW(3),1,NOW(3),'user-tourist',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-030',10,'user-sara',N'DP 斐波那契',N'## 题目理解

爬楼梯，每次可以爬 1 或 2 步，问到达楼顶有多少种方法。

## 解题思路

本质是斐波那契数列：
- 爬到第 n 阶 = 爬到第 n-1 阶 + 爬到第 n-2 阶

## 方法一：迭代

```typescript
function climbStairs(n: number): number {
    if (n <= 2) return n;
    
    let prev1 = 2, prev2 = 1;
    for (let i = 3; i <= n; i++) {
        const curr = prev1 + prev2;
        prev2 = prev1;
        prev1 = curr;
    }
    
    return prev1;
}
```

## 方法二：递归 + 记忆化

```typescript
function climbStairsMemo(n: number, memo: number[] = []): number {
    if (n <= 2) return n;
    if (memo[n]) return memo[n];
    
    memo[n] = climbStairsMemo(n-1, memo) + climbStairsMemo(n-2, memo);
    return memo[n];
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(1) 或 O(n)',N'迭代 O(1) 空间解法','typescript','["dynamic-programming","fibonacci"]',338,NOW(3),NOW(3),1,NOW(3),'user-sara',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-031',10,'user-max',N'问题 10 解法',N'## 问题 10 解法

使用标准算法思路解决。',N'问题 10 的解法','typescript','["algorithm"]',43,NOW(3),NOW(3),1,NOW(3),'user-max',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-032',10,'user-petr',N'问题 10 解法',N'## 问题 10 解法

使用标准算法思路解决。',N'问题 10 的解法','typescript','["algorithm"]',317,NOW(3),NOW(3),1,NOW(3),'user-petr',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-033',11,'user-emma',N'双指针',N'## 题目理解

找出两条线使得它们与 x 轴构成的容器能容纳最多水。

## 解题思路

使用双指针从两端向中间收敛：
- 移动较短的那条边可能找到更大的面积
- 证明：移动较长的边只会使宽度减小而高度不会增加

## 方法一：双指针

```typescript
function maxArea(height: number[]): number {
    let left = 0, right = height.length - 1;
    let maxArea = 0;
    
    while (left < right) {
        const width = right - left;
        const h = Math.min(height[left], height[right]);
        maxArea = Math.max(maxArea, width * h);
        
        // 移动较短的边
        if (height[left] < height[right]) {
            left++;
        } else {
            right--;
        }
    }
    
    return maxArea;
}
```

## 复杂度分析
- 时间复杂度：O(n)，单次遍历
- 空间复杂度：O(1)',N'双指针最优解','typescript','["two-pointers","greedy"]',131,NOW(3),NOW(3),1,NOW(3),'user-emma',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-034',11,'user-lily',N'问题 11 解法',N'## 问题 11 解法

使用标准算法思路解决。',N'问题 11 的解法','typescript','["algorithm"]',396,NOW(3),NOW(3),1,NOW(3),'user-lily',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-035',12,'user-scott',N'模拟转换',N'## 题目理解

将整数转换为罗马数字表示。

## 解题思路

建立从大到小的值映射表，贪心转换：
- M=1000, CM=900, D=500, CD=400
- C=100, XC=90, L=50, XL=40
- X=10, IX=9, V=5, IV=4, I=1

## 方法

```python
def intToRoman(num: int) -> str:
    val = [
        1000, 900, 500, 400,
        100, 90, 50, 40,
        10, 9, 5, 4,
        1
    ]
    sym = [
        'M', 'CM', 'D', 'CD',
        'C', 'XC', 'L', 'XL',
        'X', 'IX', 'V', 'IV',
        'I'
    ]
    
    result = ''
    for i in range(len(val)):
        # 重复次数
        count = num // val[i]
        result += sym[i] * count
        num -= val[i] * count
    
    return result
```

## 复杂度分析
- 时间复杂度：O(1)，最多循环 13 次
- 空间复杂度：O(1)',N'贪心模拟解法','python','["math","simulation"]',362,NOW(3),NOW(3),1,NOW(3),'user-scott',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-036',12,'user-tom',N'问题 12 解法',N'## 问题 12 解法

使用标准算法思路解决。',N'问题 12 的解法','typescript','["algorithm"]',389,NOW(3),NOW(3),1,NOW(3),'user-tom',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-037',13,'user-david',N'双指针',N'## 题目理解

判断字符串是否是回文，只考虑字母和数字字符。

## 解题思路

使用双指针从两端向中间检查：
- 先将字符串转为小写并过滤非字母数字字符
- 比较两端指针指向的字符

## 方法一：双指针

```typescript
function isPalindrome(s: string): boolean {
    // 过滤并转小写
    const filtered = s.toLowerCase().replace(/[^a-z0-9]/g, '');
    
    let left = 0, right = filtered.length - 1;
    while (left < right) {
        if (filtered[left] !== filtered[right]) {
            return false;
        }
        left++;
        right--;
    }
    
    return true;
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(n)',N'双指针简洁实现','typescript','["two-pointers","string"]',309,NOW(3),NOW(3),1,NOW(3),'user-david',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-038',13,'user-kevin',N'问题 13 解法',N'## 问题 13 解法

使用标准算法思路解决。',N'问题 13 的解法','typescript','["algorithm"]',244,NOW(3),NOW(3),1,NOW(3),'user-kevin',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-039',13,'user-benq',N'问题 13 解法',N'## 问题 13 解法

使用标准算法思路解决。',N'问题 13 的解法','typescript','["algorithm"]',142,NOW(3),NOW(3),1,NOW(3),'user-benq',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-040',13,'user-ecnerwala',N'问题 13 解法',N'## 问题 13 解法

使用标准算法思路解决。',N'问题 13 的解法','typescript','["algorithm"]',259,NOW(3),NOW(3),1,NOW(3),'user-ecnerwala',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-041',14,'user-jiangly',N'排序分组',N'## 题目理解

将字母异位词分组。

## 解题思路

关键洞察：字母异位词的排序结果相同
- 将每个字符串排序作为 key
- 用 hash map 分组

## 方法一：排序分组

```typescript
function groupAnagrams(strs: string[]): string[][] {
    const map = new Map<string, string[]>();
    
    for (const str of strs) {
        // 排序作为 key
        const key = str.split('').sort().join('');
        
        if (!map.has(key)) {
            map.set(key, []);
        }
        map.get(key)!.push(str);
    }
    
    return Array.from(map.values());
}
```

## 方法二：计数分组

```typescript
function groupAnagramsCount(strs: string[]): string[][] {
    const map = new Map<string, string[]>();
    const code = 'a'.charCodeAt(0);
    
    for (const str of strs) {
        const count = new Array(26).fill(0);
        for (const c of str) {
            count[c.charCodeAt(0) - code]++;
        }
        const key = count.join('#');
        
        if (!map.has(key)) {
            map.set(key, []);
        }
        map.get(key)!.push(str);
    }
    
    return Array.from(map.values());
}
```

## 复杂度分析
- 时间复杂度：O(n * k log k)，k 是字符串平均长度
- 空间复杂度：O(n * k)',N'排序分组法','typescript','["hash-map","sorting"]',331,NOW(3),NOW(3),1,NOW(3),'user-jiangly',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-042',14,'user-um',N'问题 14 解法',N'## 问题 14 解法

使用标准算法思路解决。',N'问题 14 的解法','typescript','["algorithm"]',172,NOW(3),NOW(3),1,NOW(3),'user-um',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-043',15,'user-yuki',N'双指针',N'## 题目理解

找出数组中所有和为 0 的三元组。

## 解题思路

排序 + 双指针：
1. 排序数组
2. 固定一个数，使用双指针找另外两个数
3. 跳过重复元素避免重复结果

## 方法

```typescript
function threeSum(nums: number[]): number[][] {
    nums.sort((a, b) => a - b);
    const result: number[][] = [];
    const n = nums.length;
    
    for (let i = 0; i < n - 2; i++) {
        if (nums[i] > 0) break;  // 剪枝
        if (i > 0 && nums[i] === nums[i-1]) continue;  // 去重
        
        let left = i + 1, right = n - 1;
        while (left < right) {
            const sum = nums[i] + nums[left] + nums[right];
            
            if (sum === 0) {
                result.push([nums[i], nums[left], nums[right]]);
                left++;
                right--;
                while (left < right && nums[left] === nums[left-1]) left++;
                while (left < right && nums[right] === nums[right+1]) right--;
            } else if (sum < 0) {
                left++;
            } else {
                right--;
            }
        }
    }
    
    return result;
}
```

## 复杂度分析
- 时间复杂度：O(n²)
- 空间复杂度：O(1)',N'排序双指针解法','typescript','["two-pointers","array"]',33,NOW(3),NOW(3),1,NOW(3),'user-yuki',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-044',15,'user-alex',N'问题 15 解法',N'## 问题 15 解法

使用标准算法思路解决。',N'问题 15 的解法','typescript','["algorithm"]',111,NOW(3),NOW(3),1,NOW(3),'user-alex',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-045',16,'user-chen',N'双指针变体',N'## 题目理解

找出数组中三数之和最接近目标值的组合。

## 解题思路

类似 3sum，双指针逼近目标值。

## 方法

```python
def threeSumClosest(nums: list, target: int) -> int:
    nums.sort()
    n = len(nums)
    closest = float('inf')
    result = 0
    
    for i in range(n - 2):
        left, right = i + 1, n - 1
        while left < right:
            s = nums[i] + nums[left] + nums[right]
            
            if abs(s - target) < closest:
                closest = abs(s - target)
                result = s
            
            if s == target:
                return target
            elif s < target:
                left += 1
            else:
                right -= 1
    
    return result
```

## 复杂度分析
- 时间复杂度：O(n²)
- 空间复杂度：O(1)',N'双指针逼近解法','python','["two-pointers","greedy"]',387,NOW(3),NOW(3),1,NOW(3),'user-chen',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-046',16,'user-tourist',N'问题 16 解法',N'## 问题 16 解法

使用标准算法思路解决。',N'问题 16 的解法','typescript','["algorithm"]',246,NOW(3),NOW(3),1,NOW(3),'user-tourist',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-047',17,'user-sara',N'回溯',N'## 题目理解

根据数字键盘生成所有可能的字母组合。

## 解题思路

典型的回溯/DFS 问题：
- 每个数字对应多个字母
- 依次选择每个位置的字母

## 方法

```typescript
function letterCombinations(digits: string): string[] {
    if (!digits) return [];
    
    const map: Record<string, string> = {
        '2': 'abc', '3': 'def', '4': 'ghi', '5': 'jkl',
        '6': 'mno', '7': 'pqrs', '8': 'tuv', '9': 'wxyz'
    };
    
    const result: string[] = [];
    
    function backtrack(index: number, current: string) {
        if (index === digits.length) {
            result.push(current);
            return;
        }
        
        const letters = map[digits[index]];
        for (const letter of letters) {
            backtrack(index + 1, current + letter);
        }
    }
    
    backtrack(0, '');
    return result;
}
```

## 复杂度分析
- 时间复杂度：O(4^n)，n 是数字长度
- 空间复杂度：O(n)',N'回溯经典解法','typescript','["backtracking","dfs"]',204,NOW(3),NOW(3),1,NOW(3),'user-sara',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-048',17,'user-max',N'问题 17 解法',N'## 问题 17 解法

使用标准算法思路解决。',N'问题 17 的解法','typescript','["algorithm"]',172,NOW(3),NOW(3),1,NOW(3),'user-max',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-049',18,'user-petr',N'双指针变体',N'## 题目理解

找出数组中所有和为目标值的四元组。

## 解题思路

排序 + 双指针的扩展：
- 两层循环固定两个数
- 双指针找另外两个数
- 注意去重

## 方法

```typescript
function fourSum(nums: number[], target: number): number[][] {
    nums.sort((a, b) => a - b);
    const result: number[][] = [];
    const n = nums.length;
    
    for (let i = 0; i < n - 3; i++) {
        if (i > 0 && nums[i] === nums[i-1]) continue;
        
        for (let j = i + 1; j < n - 2; j++) {
            if (j > i + 1 && nums[j] === nums[j-1]) continue;
            
            let left = j + 1, right = n - 1;
            while (left < right) {
                const sum = nums[i] + nums[j] + nums[left] + nums[right];
                
                if (sum === target) {
                    result.push([nums[i], nums[j], nums[left], nums[right]]);
                    left++; right--;
                    while (left < right && nums[left] === nums[left-1]) left++;
                    while (left < right && nums[right] === nums[right+1]) right--;
                } else if (sum < target) {
                    left++;
                } else {
                    right--;
                }
            }
        }
    }
    
    return result;
}
```

## 复杂度分析
- 时间复杂度：O(n³)
- 空间复杂度：O(1)',N'排序双指针扩展','typescript','["two-pointers","k-sum"]',109,NOW(3),NOW(3),1,NOW(3),'user-petr',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-050',18,'user-emma',N'问题 18 解法',N'## 问题 18 解法

使用标准算法思路解决。',N'问题 18 的解法','typescript','["algorithm"]',140,NOW(3),NOW(3),1,NOW(3),'user-emma',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-051',19,'user-lily',N'快慢指针',N'## 题目理解

删除链表的倒数第 N 个节点。

## 解题思路

使用快慢指针：
1. 快指针先走 N 步
2. 然后快慢指针一起走
3. 当快指针到达末尾时，慢指针指向待删除节点的前一个

## 方法

```typescript
function removeNthFromEnd(head: ListNode | null, n: number): ListNode | null {
    const dummy = new ListNode(0, head);
    let fast: ListNode = dummy;
    let slow: ListNode = dummy;
    
    // 快指针先走 n+1 步
    for (let i = 0; i <= n; i++) {
        fast = fast.next!;
    }
    
    // 一起走
    while (fast !== null) {
        slow = slow.next!;
        fast = fast.next;
    }
    
    // 删除节点
    slow.next = slow.next!.next;
    
    return dummy.next;
}
```

## 复杂度分析
- 时间复杂度：O(L)，L 是链表长度
- 空间复杂度：O(1)',N'快慢指针一次遍历','typescript','["linked-list","two-pointers"]',202,NOW(3),NOW(3),1,NOW(3),'user-lily',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-052',19,'user-scott',N'问题 19 解法',N'## 问题 19 解法

使用标准算法思路解决。',N'问题 19 的解法','typescript','["algorithm"]',82,NOW(3),NOW(3),1,NOW(3),'user-scott',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-053',20,'user-tom',N'栈',N'## 题目理解

判断括号字符串是否有效。

## 解题思路

使用栈来匹配：
- 左括号入栈
- 右括号时检查栈顶是否匹配
- 最后检查栈是否为空

## 方法

```typescript
function isValid(s: string): boolean {
    const stack: string[] = [];
    const map: Record<string, string> = {
        ')': '(', 
        ']': '[', 
        '}': '{'
    };
    
    for (const char of s) {
        if ('([{'.includes(char)) {
            stack.push(char);
        } else {
            if (stack.pop() !== map[char]) {
                return false;
            }
        }
    }
    
    return stack.length === 0;
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(n)',N'栈匹配解法','typescript','["stack","string"]',77,NOW(3),NOW(3),1,NOW(3),'user-tom',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-054',20,'user-david',N'问题 20 解法',N'## 问题 20 解法

使用标准算法思路解决。',N'问题 20 的解法','typescript','["algorithm"]',224,NOW(3),NOW(3),1,NOW(3),'user-david',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-055',20,'user-kevin',N'问题 20 解法',N'## 问题 20 解法

使用标准算法思路解决。',N'问题 20 的解法','typescript','["algorithm"]',79,NOW(3),NOW(3),1,NOW(3),'user-kevin',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-056',20,'user-benq',N'问题 20 解法',N'## 问题 20 解法

使用标准算法思路解决。',N'问题 20 的解法','typescript','["algorithm"]',213,NOW(3),NOW(3),1,NOW(3),'user-benq',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-057',21,'user-ecnerwala',N'迭代/递归',N'## 题目理解

合并两个有序链表。

## 解题思路

同时遍历两个链表，按大小顺序连接。

## 方法一：迭代

```typescript
function mergeTwoLists(l1: ListNode | null, l2: ListNode | null): ListNode | null {
    const dummy = new ListNode(0);
    let current = dummy;
    
    while (l1 && l2) {
        if (l1.val <= l2.val) {
            current.next = l1;
            l1 = l1.next;
        } else {
            current.next = l2;
            l2 = l2.next;
        }
        current = current.next;
    }
    
    current.next = l1 || l2;
    return dummy.next;
}
```

## 方法二：递归

```typescript
function mergeTwoListsRecursive(l1: ListNode | null, l2: ListNode | null): ListNode | null {
    if (!l1 || !l2) return l1 || l2;
    
    if (l1.val <= l2.val) {
        l1.next = mergeTwoListsRecursive(l1.next, l2);
        return l1;
    } else {
        l2.next = mergeTwoListsRecursive(l1, l2.next);
        return l2;
    }
}
```

## 复杂度分析
- 时间复杂度：O(m+n)
- 空间复杂度：O(1) 或 O(m+n)',N'迭代最优解','typescript','["linked-list","merge"]',206,NOW(3),NOW(3),1,NOW(3),'user-ecnerwala',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-058',21,'user-jiangly',N'问题 21 解法',N'## 问题 21 解法

使用标准算法思路解决。',N'问题 21 的解法','typescript','["algorithm"]',339,NOW(3),NOW(3),1,NOW(3),'user-jiangly',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-059',21,'user-um',N'问题 21 解法',N'## 问题 21 解法

使用标准算法思路解决。',N'问题 21 的解法','typescript','["algorithm"]',165,NOW(3),NOW(3),1,NOW(3),'user-um',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-060',22,'user-yuki',N'回溯',N'## 题目理解

生成所有有效的括号组合。

## 解题思路

回溯构造括号串：
- 左括号随时可以加
- 右括号需要左括号数量 > 右括号数量
- 左右括号用完即完成

## 方法

```python
def generateParenthesis(n: int) -> list:
    result = []
    
    def backtrack(s='', left=0, right=0):
        if len(s) == 2 * n:
            result.append(s)
            return
        
        if left < n:
            backtrack(s + '(', left + 1, right)
        
        if right < left:
            backtrack(s + ')', left, right + 1)
    
    backtrack()
    return result
```

## 复杂度分析
- 时间复杂度：卡特兰数 O(C(2n,n)/(n+1))
- 空间复杂度：O(n)',N'回溯构造','python','["backtracking","dfs"]',52,NOW(3),NOW(3),1,NOW(3),'user-yuki',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-061',22,'user-alex',N'问题 22 解法',N'## 问题 22 解法

使用标准算法思路解决。',N'问题 22 的解法','typescript','["algorithm"]',265,NOW(3),NOW(3),1,NOW(3),'user-alex',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-062',23,'user-chen',N'迭代',N'## 题目理解

两两交换链表相邻节点。

## 解题思路

使用 dummy 节点简化边界处理。

## 方法

```typescript
function swapPairs(head: ListNode | null): ListNode | null {
    const dummy = new ListNode(0, head);
    let current = dummy;
    
    while (current.next && current.next.next) {
        const first = current.next;
        const second = current.next.next;
        
        // 交换
        first.next = second.next;
        second.next = first;
        current.next = second;
        
        current = first;
    }
    
    return dummy.next;
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(1)',N'dummy 节点简化','typescript','["linked-list"]',304,NOW(3),NOW(3),1,NOW(3),'user-chen',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-063',23,'user-tourist',N'问题 23 解法',N'## 问题 23 解法

使用标准算法思路解决。',N'问题 23 的解法','typescript','["algorithm"]',93,NOW(3),NOW(3),1,NOW(3),'user-tourist',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-064',24,'user-sara',N'递归/迭代',N'## 题目理解

二叉树中序遍历。

## 解题思路

左子树 -> 根 -> 右子树的顺序遍历。

## 方法一：递归

```typescript
function inorderTraversal(root: TreeNode | null): number[] {
    const result: number[] = [];
    
    function inorder(node: TreeNode | null) {
        if (!node) return;
        inorder(node.left);
        result.push(node.val);
        inorder(node.right);
    }
    
    inorder(root);
    return result;
}
```

## 方法二：迭代（栈）

```typescript
function inorderIterative(root: TreeNode | null): number[] {
    const result: number[] = [];
    const stack: TreeNode[] = [];
    let current = root;
    
    while (current || stack.length) {
        while (current) {
            stack.push(current);
            current = current.left;
        }
        current = stack.pop()!;
        result.push(current.val);
        current = current.right;
    }
    
    return result;
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(h)，h 为树高',N'递归和迭代两种解法','typescript','["binary-tree","stack"]',223,NOW(3),NOW(3),1,NOW(3),'user-sara',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-065',24,'user-max',N'问题 24 解法',N'## 问题 24 解法

使用标准算法思路解决。',N'问题 24 的解法','typescript','["algorithm"]',70,NOW(3),NOW(3),1,NOW(3),'user-max',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-066',24,'user-petr',N'问题 24 解法',N'## 问题 24 解法

使用标准算法思路解决。',N'问题 24 的解法','typescript','["algorithm"]',312,NOW(3),NOW(3),1,NOW(3),'user-petr',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-067',25,'user-emma',N'二分',N'## 题目理解

在旋转排序数组中搜索目标值。

## 解题思路

二分查找变形：
- 判断哪半边是有序的
- 根据目标值和边界比较确定搜索范围

## 方法

```typescript
function search(nums: number[], target: number): number {
    let left = 0, right = nums.length - 1;
    
    while (left <= right) {
        const mid = Math.floor((left + right) / 2);
        
        if (nums[mid] === target) return mid;
        
        // 左半边有序
        if (nums[left] <= nums[mid]) {
            if (nums[left] <= target && target < nums[mid]) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        } else {  // 右半边有序
            if (nums[mid] < target && target <= nums[right]) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
    }
    
    return -1;
}
```

## 复杂度分析
- 时间复杂度：O(log n)
- 空间复杂度：O(1)',N'二分查找变形','typescript','["binary-search"]',180,NOW(3),NOW(3),1,NOW(3),'user-emma',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-068',25,'user-lily',N'问题 25 解法',N'## 问题 25 解法

使用标准算法思路解决。',N'问题 25 的解法','typescript','["algorithm"]',351,NOW(3),NOW(3),1,NOW(3),'user-lily',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-069',26,'user-scott',N'迭代/递归',N'## 题目理解

反转链表。

## 解题思路

迭代：保存下一个节点，改变当前节点的 next 指向。

## 方法一：迭代

```typescript
function reverseList(head: ListNode | null): ListNode | null {
    let prev = null;
    let current = head;
    
    while (current) {
        const next = current.next;
        current.next = prev;
        prev = current;
        current = next;
    }
    
    return prev;
}
```

## 方法二：递归

```typescript
function reverseRecursive(head: ListNode | null): ListNode | null {
    if (!head || !head.next) return head;
    
    const newHead = reverseRecursive(head.next);
    head.next.next = head;
    head.next = null;
    
    return newHead;
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(1) 或 O(n)',N'迭代 O(1) 空间','typescript','["linked-list"]',346,NOW(3),NOW(3),1,NOW(3),'user-scott',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-070',26,'user-tom',N'问题 26 解法',N'## 问题 26 解法

使用标准算法思路解决。',N'问题 26 的解法','typescript','["algorithm"]',215,NOW(3),NOW(3),1,NOW(3),'user-tom',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-071',26,'user-david',N'问题 26 解法',N'## 问题 26 解法

使用标准算法思路解决。',N'问题 26 的解法','typescript','["algorithm"]',325,NOW(3),NOW(3),1,NOW(3),'user-david',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-072',27,'user-kevin',N'哈希集合',N'## 题目理解

判断数组是否有重复元素。

## 解题思路

使用哈希集合检测重复。

## 方法

```typescript
function containsDuplicate(nums: number[]): boolean {
    const seen = new Set<number>();
    
    for (const num of nums) {
        if (seen.has(num)) {
            return true;
        }
        seen.add(num);
    }
    
    return false;
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(n)',N'哈希集合检测','typescript','["hash-set"]',128,NOW(3),NOW(3),1,NOW(3),'user-kevin',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-073',27,'user-benq',N'问题 27 解法',N'## 问题 27 解法

使用标准算法思路解决。',N'问题 27 的解法','typescript','["algorithm"]',390,NOW(3),NOW(3),1,NOW(3),'user-benq',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-074',27,'user-ecnerwala',N'问题 27 解法',N'## 问题 27 解法

使用标准算法思路解决。',N'问题 27 的解法','typescript','["algorithm"]',65,NOW(3),NOW(3),1,NOW(3),'user-ecnerwala',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-075',28,'user-jiangly',N'滑动窗口',N'## 题目理解

找出和大于等于目标值的最小连续子数组长度。

## 解题思路

使用滑动窗口：
- 右指针扩展窗口
- 左指针收缩窗口

## 方法

```typescript
function minSubArrayLen(target: number, nums: number[]): number {
    let left = 0;
    let sum = 0;
    let result = Infinity;
    
    for (let right = 0; right < nums.length; right++) {
        sum += nums[right];
        
        while (sum >= target) {
            result = Math.min(result, right - left + 1);
            sum -= nums[left];
            left++;
        }
    }
    
    return result === Infinity ? 0 : result;
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(1)',N'滑动窗口最优解','typescript','["sliding-window","two-pointers"]',53,NOW(3),NOW(3),1,NOW(3),'user-jiangly',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-076',28,'user-um',N'问题 28 解法',N'## 问题 28 解法

使用标准算法思路解决。',N'问题 28 的解法','typescript','["algorithm"]',368,NOW(3),NOW(3),1,NOW(3),'user-um',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-077',29,'user-yuki',N'BFS 队列',N'## 题目理解

层序遍历二叉树。

## 解题思路

使用队列进行 BFS 遍历。

## 方法

```typescript
function levelOrder(root: TreeNode | null): number[][] {
    if (!root) return [];
    
    const result: number[][] = [];
    const queue: TreeNode[] = [root];
    
    while (queue.length) {
        const levelSize = queue.length;
        const level: number[] = [];
        
        for (let i = 0; i < levelSize; i++) {
            const node = queue.shift()!;
            level.push(node.val);
            
            if (node.left) queue.push(node.left);
            if (node.right) queue.push(node.right);
        }
        
        result.push(level);
    }
    
    return result;
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(w)，w 为最大宽度',N'BFS 层序遍历','typescript','["bfs","tree"]',146,NOW(3),NOW(3),1,NOW(3),'user-yuki',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-078',29,'user-alex',N'问题 29 解法',N'## 问题 29 解法

使用标准算法思路解决。',N'问题 29 的解法','typescript','["algorithm"]',178,NOW(3),NOW(3),1,NOW(3),'user-alex',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-079',30,'user-chen',N'二分',N'## 题目理解

在二维矩阵中搜索目标值，每行有序且下一行第一个元素大于上一行最后一个元素。

## 解题思路

两次二分或一次二分展开。

## 方法一：两次二分

```typescript
function searchMatrix(matrix: number[][], target: number): boolean {
    const m = matrix.length, n = matrix[0].length;
    let top = 0, bottom = m - 1;
    
    // 找目标行
    while (top <= bottom) {
        const mid = Math.floor((top + bottom) / 2);
        if (matrix[mid][n-1] < target) {
            top = mid + 1;
        } else if (matrix[mid][0] > target) {
            bottom = mid - 1;
        } else {
            // 在这一行
            let left = 0, right = n - 1;
            while (left <= right) {
                const colMid = Math.floor((left + right) / 2);
                if (matrix[mid][colMid] === target) return true;
                if (matrix[mid][colMid] < target) left = colMid + 1;
                else right = colMid - 1;
            }
            return false;
        }
    }
    
    return false;
}
```

## 方法二：一次二分（将二维转一维）

```typescript
function searchMatrixFlat(matrix: number[][], target: number): boolean {
    const m = matrix.length, n = matrix[0].length;
    let left = 0, right = m * n - 1;
    
    while (left <= right) {
        const mid = Math.floor((left + right) / 2);
        const row = Math.floor(mid / n);
        const col = mid % n;
        
        if (matrix[row][col] === target) return true;
        if (matrix[row][col] < target) left = mid + 1;
        else right = mid - 1;
    }
    
    return false;
}
```

## 复杂度分析
- 时间复杂度：O(log(m*n))
- 空间复杂度：O(1)',N'一次二分最优','typescript','["binary-search","matrix"]',70,NOW(3),NOW(3),1,NOW(3),'user-chen',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-080',30,'user-tourist',N'问题 30 解法',N'## 问题 30 解法

使用标准算法思路解决。',N'问题 30 的解法','typescript','["algorithm"]',149,NOW(3),NOW(3),1,NOW(3),'user-tourist',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-081',31,'user-sara',N'DP',N'## 题目理解

机器人从左上角到右下角有多少条唯一路径（只能向下或向右）。

## 解题思路

动态规划：dp[i][j] = dp[i-1][j] + dp[i][j-1]

## 方法一：二维 DP

```typescript
function uniquePaths(m: number, n: number): number {
    const dp = Array(m).fill(0).map(() => Array(n).fill(0));
    
    for (let i = 0; i < m; i++) dp[i][0] = 1;
    for (let j = 0; j < n; j++) dp[0][j] = 1;
    
    for (let i = 1; i < m; i++) {
        for (let j = 1; j < n; j++) {
            dp[i][j] = dp[i-1][j] + dp[i][j-1];
        }
    }
    
    return dp[m-1][n-1];
}
```

## 方法二：一维 DP

```typescript
function uniquePathsOptimized(m: number, n: number): number {
    const dp = Array(n).fill(1);
    
    for (let i = 1; i < m; i++) {
        for (let j = 1; j < n; j++) {
            dp[j] += dp[j-1];
        }
    }
    
    return dp[n-1];
}
```

## 复杂度分析
- 时间复杂度：O(m*n)
- 空间复杂度：O(n)',N'一维 DP 优化','typescript','["dynamic-programming"]',81,NOW(3),NOW(3),1,NOW(3),'user-sara',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-082',31,'user-max',N'问题 31 解法',N'## 问题 31 解法

使用标准算法思路解决。',N'问题 31 的解法','typescript','["algorithm"]',224,NOW(3),NOW(3),1,NOW(3),'user-max',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-083',32,'user-petr',N'DP',N'## 题目理解

有障碍物的网格中计算唯一路径数。

## 解题思路

遇到障碍跳过，dp 值设为 0。

## 方法

```typescript
function uniquePathsWithObstacles(obstacleGrid: number[][]): number {
    const m = obstacleGrid.length, n = obstacleGrid[0].length;
    const dp = Array(n).fill(0);
    dp[0] = obstacleGrid[0][0] === 0 ? 1 : 0;
    
    for (let i = 0; i < m; i++) {
        for (let j = 0; j < n; j++) {
            if (obstacleGrid[i][j] === 1) {
                dp[j] = 0;
            } else if (j > 0) {
                dp[j] += dp[j-1];
            }
        }
    }
    
    return dp[n-1];
}
```

## 复杂度分析
- 时间复杂度：O(m*n)
- 空间复杂度：O(n)',N'一维 DP 解法','typescript','["dynamic-programming","grid"]',172,NOW(3),NOW(3),1,NOW(3),'user-petr',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-084',32,'user-emma',N'问题 32 解法',N'## 问题 32 解法

使用标准算法思路解决。',N'问题 32 的解法','typescript','["algorithm"]',262,NOW(3),NOW(3),1,NOW(3),'user-emma',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-085',33,'user-lily',N'哈希表 + 双向链表',N'## 题目理解

实现 LRU 缓存。

## 解题思路

使用哈希表 + 双向链表：
- 哈希表提供 O(1) 查找
- 双向链表维护访问顺序

## 方法

```typescript
class LRUCache {
    private map = new Map<number, number>();
    private capacity: number;
    
    constructor(capacity: number) {
        this.capacity = capacity;
    }
    
    get(key: number): number {
        if (!this.map.has(key)) return -1;
        
        // 重新插入以更新位置
        const value = this.map.get(key)!;
        this.map.delete(key);
        this.map.set(key, value);
        
        return value;
    }
    
    put(key: number, value: number): void {
        if (this.map.has(key)) {
            this.map.delete(key);
        } else if (this.map.size >= this.capacity) {
            // 删除最老的（迭代器的第一个）
            const firstKey = this.map.keys().next().value;
            this.map.delete(firstKey);
        }
        this.map.set(key, value);
    }
}
```

## 复杂度分析
- 时间复杂度：O(1)
- 空间复杂度：O(capacity)',N'Map 实现 LRU','typescript','["hash-map","linked-list","design"]',355,NOW(3),NOW(3),1,NOW(3),'user-lily',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-086',33,'user-scott',N'问题 33 解法',N'## 问题 33 解法

使用标准算法思路解决。',N'问题 33 的解法','typescript','["algorithm"]',216,NOW(3),NOW(3),1,NOW(3),'user-scott',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-087',33,'user-tom',N'问题 33 解法',N'## 问题 33 解法

使用标准算法思路解决。',N'问题 33 的解法','typescript','["algorithm"]',113,NOW(3),NOW(3),1,NOW(3),'user-tom',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-088',34,'user-david',N'荷兰国旗',N'## 题目理解

将 0、1、2 三种颜色排序（荷兰国旗问题）。

## 解题思路

三路快排的思想：
- [0, low) 都是 0
- [low, mid) 都是 1
- [high, n) 都是 2

## 方法

```typescript
function sortColors(nums: number[]): void {
    let low = 0, mid = 0, high = nums.length - 1;
    
    while (mid <= high) {
        if (nums[mid] === 0) {
            [nums[low], nums[mid]] = [nums[mid], nums[low]];
            low++;
            mid++;
        } else if (nums[mid] === 1) {
            mid++;
        } else {  // nums[mid] === 2
            [nums[mid], nums[high]] = [nums[high], nums[mid]];
            high--;
        }
    }
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(1)',N'三路分区最优解','typescript','["three-pointers","sorting"]',219,NOW(3),NOW(3),1,NOW(3),'user-david',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-089',34,'user-kevin',N'问题 34 解法',N'## 问题 34 解法

使用标准算法思路解决。',N'问题 34 的解法','typescript','["algorithm"]',211,NOW(3),NOW(3),1,NOW(3),'user-kevin',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-090',35,'user-benq',N'递归/迭代',N'## 题目理解

二叉树前序遍历。

## 解题思路

根 -> 左 -> 右 的顺序遍历。

## 方法一：递归

```typescript
function preorderTraversal(root: TreeNode | null): number[] {
    const result: number[] = [];
    
    function preorder(node: TreeNode | null) {
        if (!node) return;
        result.push(node.val);
        preorder(node.left);
        preorder(node.right);
    }
    
    preorder(root);
    return result;
}
```

## 方法二：迭代（栈）

```typescript
function preorderIterative(root: TreeNode | null): number[] {
    if (!root) return [];
    
    const result: number[] = [];
    const stack: TreeNode[] = [root];
    
    while (stack.length) {
        const node = stack.pop()!;
        result.push(node.val);
        
        if (node.right) stack.push(node.right);
        if (node.left) stack.push(node.left);
    }
    
    return result;
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(h)',N'迭代实现','typescript','["binary-tree","stack"]',137,NOW(3),NOW(3),1,NOW(3),'user-benq',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-091',35,'user-ecnerwala',N'问题 35 解法',N'## 问题 35 解法

使用标准算法思路解决。',N'问题 35 的解法','typescript','["algorithm"]',373,NOW(3),NOW(3),1,NOW(3),'user-ecnerwala',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-092',35,'user-jiangly',N'问题 35 解法',N'## 问题 35 解法

使用标准算法思路解决。',N'问题 35 的解法','typescript','["algorithm"]',166,NOW(3),NOW(3),1,NOW(3),'user-jiangly',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-093',36,'user-um',N'迭代',N'## 题目理解

二叉树后序遍历。

## 解题思路

前序遍历的变形：根 -> 右 -> 左，然后反转结果。

## 方法

```typescript
function postorderTraversal(root: TreeNode | null): number[] {
    if (!root) return [];
    
    const result: number[] = [];
    const stack: TreeNode[] = [root];
    
    while (stack.length) {
        const node = stack.pop()!;
        result.push(node.val);
        
        if (node.left) stack.push(node.left);
        if (node.right) stack.push(node.right);
    }
    
    return result.reverse();
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(h)',N'反转技巧','typescript','["binary-tree","stack"]',389,NOW(3),NOW(3),1,NOW(3),'user-um',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-094',36,'user-yuki',N'问题 36 解法',N'## 问题 36 解法

使用标准算法思路解决。',N'问题 36 的解法','typescript','["algorithm"]',379,NOW(3),NOW(3),1,NOW(3),'user-yuki',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-095',37,'user-alex',N'二分',N'## 题目理解

在有序数组中搜索目标值。

## 解题思路

标准二分查找。

## 方法

```typescript
function search(nums: number[], target: number): number {
    let left = 0, right = nums.length - 1;
    
    while (left <= right) {
        const mid = Math.floor((left + right) / 2);
        
        if (nums[mid] === target) return mid;
        if (nums[mid] < target) left = mid + 1;
        else right = mid - 1;
    }
    
    return -1;
}
```

## 复杂度分析
- 时间复杂度：O(log n)
- 空间复杂度：O(1)',N'标准二分','typescript','["binary-search"]',361,NOW(3),NOW(3),1,NOW(3),'user-alex',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-096',37,'user-chen',N'问题 37 解法',N'## 问题 37 解法

使用标准算法思路解决。',N'问题 37 的解法','typescript','["algorithm"]',66,NOW(3),NOW(3),1,NOW(3),'user-chen',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-097',37,'user-tourist',N'问题 37 解法',N'## 问题 37 解法

使用标准算法思路解决。',N'问题 37 的解法','typescript','["algorithm"]',341,NOW(3),NOW(3),1,NOW(3),'user-tourist',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-098',37,'user-sara',N'问题 37 解法',N'## 问题 37 解法

使用标准算法思路解决。',N'问题 37 的解法','typescript','["algorithm"]',355,NOW(3),NOW(3),1,NOW(3),'user-sara',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-099',38,'user-max',N'堆/快排思想',N'## 题目理解

找出第 K 大的元素。

## 解题思路

使用最小堆维护前 K 大元素，或使用快排 partition。

## 方法一：最小堆

```python
import heapq

def findKthLargest(nums: list, k: int) -> int:
    heap = []
    for num in nums:
        heapq.heappush(heap, num)
        if len(heap) > k:
            heapq.heappop(heap)
    return heap[0]
```

## 方法二：快排 partition

```python
def findKthLargestQS(nums: list, k: int) -> int:
    index = len(nums) - k  # 转换问题
    
    def partition(left, right):
        pivot = nums[right]
        p = left
        for i in range(left, right):
            if nums[i] <= pivot:
                nums[p], nums[i] = nums[i], nums[p]
                p += 1
        nums[p], nums[right] = nums[right], nums[p]
        return p
    
    left, right = 0, len(nums) - 1
    while True:
        p = partition(left, right)
        if p == index:
            return nums[p]
        elif p < index:
            left = p + 1
        else:
            right = p - 1
```

## 复杂度分析
- 时间复杂度：O(n log k) 或 O(n)
- 空间复杂度：O(k) 或 O(1)',N'快排 partition O(n)','python','["heap","quickselect"]',117,NOW(3),NOW(3),1,NOW(3),'user-max',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-100',38,'user-petr',N'问题 38 解法',N'## 问题 38 解法

使用标准算法思路解决。',N'问题 38 的解法','typescript','["algorithm"]',303,NOW(3),NOW(3),1,NOW(3),'user-petr',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-101',39,'user-emma',N'DP',N'## 题目理解

选择不相邻的房屋求最大金额。

## 解题思路

dp[i] = max(dp[i-1], dp[i-2] + nums[i])

## 方法

```typescript
function rob(nums: number[]): number {
    const n = nums.length;
    if (n === 0) return 0;
    if (n === 1) return nums[0];
    
    let prev2 = 0, prev1 = nums[0];
    
    for (let i = 1; i < n; i++) {
        const curr = Math.max(prev1, prev2 + nums[i]);
        prev2 = prev1;
        prev1 = curr;
    }
    
    return prev1;
}
```

## 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(1)',N'状态压缩 DP','typescript','["dynamic-programming"]',155,NOW(3),NOW(3),1,NOW(3),'user-emma',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-102',39,'user-lily',N'问题 39 解法',N'## 问题 39 解法

使用标准算法思路解决。',N'问题 39 的解法','typescript','["algorithm"]',113,NOW(3),NOW(3),1,NOW(3),'user-lily',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-103',40,'user-scott',N'位运算',N'## 题目理解

计算无符号整数二进制中 1 的个数。

## 解题思路

n & (n-1) 可以消除最低位的 1。

## 方法

```typescript
function hammingWeight(n: number): number {
    let count = 0;
    while (n) {
        n &= (n - 1);
        count++;
    }
    return count;
}
```

## 复杂度分析
- 时间复杂度：O(k)，k 为 1 的个数
- 空间复杂度：O(1)',N'n&(n-1) 技巧','typescript','["bit-manipulation"]',266,NOW(3),NOW(3),1,NOW(3),'user-scott',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-104',40,'user-tom',N'问题 40 解法',N'## 问题 40 解法

使用标准算法思路解决。',N'问题 40 的解法','typescript','["algorithm"]',224,NOW(3),NOW(3),1,NOW(3),'user-tom',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-105',40,'user-david',N'问题 40 解法',N'## 问题 40 解法

使用标准算法思路解决。',N'问题 40 的解法','typescript','["algorithm"]',168,NOW(3),NOW(3),1,NOW(3),'user-david',0,NULL,NULL,0,NULL,NULL);

COMMIT;
SET FOREIGN_KEY_CHECKS=1;