/**
 * Translation Seed Data
 * Contains all translations for i18n support (en-US and zh-CN)
 */
import { PROBLEM_IDS } from './problems.data';
import { CONTEST_IDS } from './contests.data';

export type TranslationSeed = {
  entity_type: string;
  entity_id: string;
  field_name: string;
  locale: string;
  content: string;
};

// ============================================================================
// PROBLEM TRANSLATIONS
// ============================================================================

export const problemTranslations: TranslationSeed[] = [
  // Two Sum
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.TWO_SUM),
    field_name: 'title',
    locale: 'en-US',
    content: 'Two Sum',
  },
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.TWO_SUM),
    field_name: 'title',
    locale: 'zh-CN',
    content: '两数之和',
  },

  // Longest Substring Without Repeating Characters
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.LONGEST_SUBSTRING),
    field_name: 'title',
    locale: 'en-US',
    content: 'Longest Substring Without Repeating Characters',
  },
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.LONGEST_SUBSTRING),
    field_name: 'title',
    locale: 'zh-CN',
    content: '无重复字符的最长子串',
  },

  // Merge Intervals
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.MERGE_INTERVALS),
    field_name: 'title',
    locale: 'en-US',
    content: 'Merge Intervals',
  },
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.MERGE_INTERVALS),
    field_name: 'title',
    locale: 'zh-CN',
    content: '合并区间',
  },

  // Median of Two Sorted Arrays
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS),
    field_name: 'title',
    locale: 'en-US',
    content: 'Median of Two Sorted Arrays',
  },
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS),
    field_name: 'title',
    locale: 'zh-CN',
    content: '寻找两个正序数组的中位数',
  },

  // Number of Islands
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.NUMBER_OF_ISLANDS),
    field_name: 'title',
    locale: 'en-US',
    content: 'Number of Islands',
  },
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.NUMBER_OF_ISLANDS),
    field_name: 'title',
    locale: 'zh-CN',
    content: '岛屿数量',
  },

  // Combine Two Tables
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.COMBINE_TWO_TABLES),
    field_name: 'title',
    locale: 'en-US',
    content: 'Combine Two Tables',
  },
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.COMBINE_TWO_TABLES),
    field_name: 'title',
    locale: 'zh-CN',
    content: '组合两个表',
  },

  // Tenth Line
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.TENTH_LINE),
    field_name: 'title',
    locale: 'en-US',
    content: 'Tenth Line',
  },
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.TENTH_LINE),
    field_name: 'title',
    locale: 'zh-CN',
    content: '第十行',
  },

  // Print FooBar Alternately
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.PRINT_FOOBAR_ALTERNATELY),
    field_name: 'title',
    locale: 'en-US',
    content: 'Print FooBar Alternately',
  },
  {
    entity_type: 'PROBLEM',
    entity_id: String(PROBLEM_IDS.PRINT_FOOBAR_ALTERNATELY),
    field_name: 'title',
    locale: 'zh-CN',
    content: '交替打印FooBar',
  },
];

// ============================================================================
// PROBLEM DETAIL TRANSLATIONS
// ============================================================================

export const problemDetailTranslations: TranslationSeed[] = [
  // Two Sum - summary
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-two-sum',
    field_name: 'summary',
    locale: 'en-US',
    content:
      'Given an array of integers `nums` and an integer `target`, return _indices of the two numbers such that they add up to `target`_.\n\nYou may assume that each input would have **exactly one solution**, and you may not use the *same* element twice.\n\nYou can return the answer in any order.',
  },
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-two-sum',
    field_name: 'summary',
    locale: 'zh-CN',
    content:
      '给定一个整数数组 `nums` 和一个整数目标值 `target`，请你在该数组中找出 **和为目标值** _target_ 的那 **两个** 整数，并返回它们的数组下标。\n\n你可以假设每种输入只会对应一个答案。但是，数组中同一个元素在答案里不能重复出现。\n\n你可以按任意顺序返回答案。',
  },
  // Two Sum - follow_up
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-two-sum',
    field_name: 'follow_up',
    locale: 'en-US',
    content:
      'Can you come up with an algorithm that is less than $O(n^2)$ time complexity?',
  },
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-two-sum',
    field_name: 'follow_up',
    locale: 'zh-CN',
    content: '你能想出一个时间复杂度小于 $O(n^2)$ 的算法吗？',
  },

  // Longest Substring - summary
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-longest-substring',
    field_name: 'summary',
    locale: 'en-US',
    content:
      'Given a string s, find the length of the longest substring without repeating characters.',
  },
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-longest-substring',
    field_name: 'summary',
    locale: 'zh-CN',
    content: '给定一个字符串 `s`，请你找出其中不含有重复字符的 **最长子串** 的长度。',
  },
  // Longest Substring - follow_up
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-longest-substring',
    field_name: 'follow_up',
    locale: 'en-US',
    content: 'Can you return the substring itself while keeping O(n) time?',
  },
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-longest-substring',
    field_name: 'follow_up',
    locale: 'zh-CN',
    content: '你能在保持 O(n) 时间复杂度的同时返回子串本身吗？',
  },

  // Merge Intervals - summary
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-merge-intervals',
    field_name: 'summary',
    locale: 'en-US',
    content:
      'Given an array of intervals where intervals[i] = [start_i, end_i], merge all overlapping intervals, and return an array of the non-overlapping intervals that cover all the intervals in the input.',
  },
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-merge-intervals',
    field_name: 'summary',
    locale: 'zh-CN',
    content:
      '以数组 `intervals` 表示若干个区间的集合，其中单个区间为 `intervals[i] = [start_i, end_i]`。请你合并所有重叠的区间，并返回一个不重叠的区间数组，该数组需恰好覆盖输入中的所有区间。',
  },
  // Merge Intervals - follow_up
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-merge-intervals',
    field_name: 'follow_up',
    locale: 'en-US',
    content:
      'How would you handle streaming intervals where you cannot keep them all in memory?',
  },
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-merge-intervals',
    field_name: 'follow_up',
    locale: 'zh-CN',
    content: '如果处理流式区间数据，无法将所有区间存储在内存中，你会如何解决？',
  },

  // Median of Two Sorted Arrays - summary
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-median-two-sorted-arrays',
    field_name: 'summary',
    locale: 'en-US',
    content:
      'Given two sorted arrays nums1 and nums2 of size m and n respectively, return the median of the two sorted arrays. The overall run time complexity should be O(log (m+n)).',
  },
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-median-two-sorted-arrays',
    field_name: 'summary',
    locale: 'zh-CN',
    content:
      '给定两个大小分别为 `m` 和 `n` 的正序（从小到大）数组 `nums1` 和 `nums2`。请你找出并返回这两个正序数组的 **中位数**。\n\n算法的时间复杂度应该为 `O(log (m+n))`。',
  },
  // Median of Two Sorted Arrays - follow_up
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-median-two-sorted-arrays',
    field_name: 'follow_up',
    locale: 'en-US',
    content:
      'Can you prove why the binary search over partitions is correct?',
  },
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-median-two-sorted-arrays',
    field_name: 'follow_up',
    locale: 'zh-CN',
    content: '你能证明为什么二分查找分区的方法是正确的吗？',
  },

  // Number of Islands - summary
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-number-of-islands',
    field_name: 'summary',
    locale: 'en-US',
    content:
      'Given an m x n 2D binary grid that represents a map of "1"s (land) and "0"s (water), return the number of islands. An island is surrounded by water and is formed by connecting adjacent lands horizontally or vertically.',
  },
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-number-of-islands',
    field_name: 'summary',
    locale: 'zh-CN',
    content:
      '给你一个由 `\'1\'`（陆地）和 `\'0\'`（水）组成的的二维网格，请你计算网格中岛屿的数量。\n\n岛屿总是被水包围，并且每座岛屿只能由水平方向和/或竖直方向上相邻的陆地连接形成。',
  },
  // Number of Islands - follow_up
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-number-of-islands',
    field_name: 'follow_up',
    locale: 'en-US',
    content:
      'How would you count islands in an online grid where cells flip from water to land?',
  },
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-number-of-islands',
    field_name: 'follow_up',
    locale: 'zh-CN',
    content: '如果是在线网格，单元格可以从水变成陆地，你如何计算岛屿数量？',
  },

  // Combine Two Tables - summary
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-combine-two-tables',
    field_name: 'summary',
    locale: 'en-US',
    content:
      'Write a SQL query to report the first name, last name, city, and state of each person in the Person table. If the address of a personId is not present in the Address table, report null instead.',
  },
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-combine-two-tables',
    field_name: 'summary',
    locale: 'zh-CN',
    content:
      '编写一个SQL查询来报告 `Person` 表中每个人的姓、名、城市和州。如果 `personId` 的地址不在 `Address` 表中，则报告为 `null`。',
  },

  // Tenth Line - summary
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-tenth-line',
    field_name: 'summary',
    locale: 'en-US',
    content:
      'Given a text file `file.txt`, print just the 10th line of the file.',
  },
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-tenth-line',
    field_name: 'summary',
    locale: 'zh-CN',
    content: '给定一个文本文件 `file.txt`，请只打印出这个文件中的第十行。',
  },

  // Print FooBar Alternately - summary
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-print-foobar',
    field_name: 'summary',
    locale: 'en-US',
    content:
      'Suppose you are given the following code... The same instance of FooBar will be passed to two different threads. Thread A will call foo() and thread B will call bar(). Modify the program to output "foobar" n times.',
  },
  {
    entity_type: 'PROBLEM_DETAIL',
    entity_id: 'pd-print-foobar',
    field_name: 'summary',
    locale: 'zh-CN',
    content:
      '假设你有以下代码... 同一个 `FooBar` 实例会被传入两个不同的线程。线程 A 将会调用 `foo()` 方法，线程 B 将会调用 `bar()` 方法。请修改程序输出 `n` 次 "foobar"。',
  },
];

// ============================================================================
// PROBLEM TAG TRANSLATIONS
// ============================================================================

export const problemTagTranslations: TranslationSeed[] = [
  // Algorithms
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'algorithms',
    field_name: 'label',
    locale: 'en-US',
    content: 'Algorithms',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'algorithms',
    field_name: 'label',
    locale: 'zh-CN',
    content: '算法',
  },
  // Array
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'array',
    field_name: 'label',
    locale: 'en-US',
    content: 'Array',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'array',
    field_name: 'label',
    locale: 'zh-CN',
    content: '数组',
  },
  // Hash Table
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'hash-table',
    field_name: 'label',
    locale: 'en-US',
    content: 'Hash Table',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'hash-table',
    field_name: 'label',
    locale: 'zh-CN',
    content: '哈希表',
  },
  // String
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'string',
    field_name: 'label',
    locale: 'en-US',
    content: 'String',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'string',
    field_name: 'label',
    locale: 'zh-CN',
    content: '字符串',
  },
  // Sliding Window
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'sliding-window',
    field_name: 'label',
    locale: 'en-US',
    content: 'Sliding Window',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'sliding-window',
    field_name: 'label',
    locale: 'zh-CN',
    content: '滑动窗口',
  },
  // Sorting
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'sorting',
    field_name: 'label',
    locale: 'en-US',
    content: 'Sorting',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'sorting',
    field_name: 'label',
    locale: 'zh-CN',
    content: '排序',
  },
  // Intervals
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'intervals',
    field_name: 'label',
    locale: 'en-US',
    content: 'Intervals',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'intervals',
    field_name: 'label',
    locale: 'zh-CN',
    content: '区间',
  },
  // Binary Search
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'binary-search',
    field_name: 'label',
    locale: 'en-US',
    content: 'Binary Search',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'binary-search',
    field_name: 'label',
    locale: 'zh-CN',
    content: '二分查找',
  },
  // Divide and Conquer
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'divide-and-conquer',
    field_name: 'label',
    locale: 'en-US',
    content: 'Divide and Conquer',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'divide-and-conquer',
    field_name: 'label',
    locale: 'zh-CN',
    content: '分治',
  },
  // DFS
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'dfs',
    field_name: 'label',
    locale: 'en-US',
    content: 'Depth-First Search',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'dfs',
    field_name: 'label',
    locale: 'zh-CN',
    content: '深度优先搜索',
  },
  // BFS
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'bfs',
    field_name: 'label',
    locale: 'en-US',
    content: 'Breadth-First Search',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'bfs',
    field_name: 'label',
    locale: 'zh-CN',
    content: '广度优先搜索',
  },
  // Matrix
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'matrix',
    field_name: 'label',
    locale: 'en-US',
    content: 'Matrix',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'matrix',
    field_name: 'label',
    locale: 'zh-CN',
    content: '矩阵',
  },
  // Database
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'database',
    field_name: 'label',
    locale: 'en-US',
    content: 'Database',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'database',
    field_name: 'label',
    locale: 'zh-CN',
    content: '数据库',
  },
  // Shell
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'shell',
    field_name: 'label',
    locale: 'en-US',
    content: 'Shell',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'shell',
    field_name: 'label',
    locale: 'zh-CN',
    content: 'Shell 脚本',
  },
  // Concurrency
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'concurrency',
    field_name: 'label',
    locale: 'en-US',
    content: 'Concurrency',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'concurrency',
    field_name: 'label',
    locale: 'zh-CN',
    content: '多线程',
  },
  // Math
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'math',
    field_name: 'label',
    locale: 'en-US',
    content: 'Math',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'math',
    field_name: 'label',
    locale: 'zh-CN',
    content: '数学',
  },
  // Dynamic Programming
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'dynamic-programming',
    field_name: 'label',
    locale: 'en-US',
    content: 'Dynamic Programming',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'dynamic-programming',
    field_name: 'label',
    locale: 'zh-CN',
    content: '动态规划',
  },
  // Greedy
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'greedy',
    field_name: 'label',
    locale: 'en-US',
    content: 'Greedy',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'greedy',
    field_name: 'label',
    locale: 'zh-CN',
    content: '贪心',
  },
  // Two Pointers
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'two-pointers',
    field_name: 'label',
    locale: 'en-US',
    content: 'Two Pointers',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'two-pointers',
    field_name: 'label',
    locale: 'zh-CN',
    content: '双指针',
  },
  // Stack
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'stack',
    field_name: 'label',
    locale: 'en-US',
    content: 'Stack',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'stack',
    field_name: 'label',
    locale: 'zh-CN',
    content: '栈',
  },
  // Queue
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'queue',
    field_name: 'label',
    locale: 'en-US',
    content: 'Queue',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'queue',
    field_name: 'label',
    locale: 'zh-CN',
    content: '队列',
  },
  // Heap
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'heap',
    field_name: 'label',
    locale: 'en-US',
    content: 'Heap (Priority Queue)',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'heap',
    field_name: 'label',
    locale: 'zh-CN',
    content: '堆（优先队列）',
  },
  // Graph
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'graph',
    field_name: 'label',
    locale: 'en-US',
    content: 'Graph',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'graph',
    field_name: 'label',
    locale: 'zh-CN',
    content: '图',
  },
  // Tree
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'tree',
    field_name: 'label',
    locale: 'en-US',
    content: 'Tree',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'tree',
    field_name: 'label',
    locale: 'zh-CN',
    content: '树',
  },
  // Design
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'design',
    field_name: 'label',
    locale: 'en-US',
    content: 'Design',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'design',
    field_name: 'label',
    locale: 'zh-CN',
    content: '设计',
  },
  // Bit Manipulation
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'bit-manipulation',
    field_name: 'label',
    locale: 'en-US',
    content: 'Bit Manipulation',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'bit-manipulation',
    field_name: 'label',
    locale: 'zh-CN',
    content: '位运算',
  },
  // Union Find
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'union-find',
    field_name: 'label',
    locale: 'en-US',
    content: 'Union Find',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'union-find',
    field_name: 'label',
    locale: 'zh-CN',
    content: '并查集',
  },
  // Linked List
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'linked-list',
    field_name: 'label',
    locale: 'en-US',
    content: 'Linked List',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'linked-list',
    field_name: 'label',
    locale: 'zh-CN',
    content: '链表',
  },
  // Recursion
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'recursion',
    field_name: 'label',
    locale: 'en-US',
    content: 'Recursion',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'recursion',
    field_name: 'label',
    locale: 'zh-CN',
    content: '递归',
  },
  // Backtracking
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'backtracking',
    field_name: 'label',
    locale: 'en-US',
    content: 'Backtracking',
  },
  {
    entity_type: 'PROBLEM_TAG',
    entity_id: 'backtracking',
    field_name: 'label',
    locale: 'zh-CN',
    content: '回溯',
  },
];

// ============================================================================
// PROBLEM EXAMPLE TRANSLATIONS
// ============================================================================

export const problemExampleTranslations: TranslationSeed[] = [
  // Two Sum Examples
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-two-sum-1',
    field_name: 'explanation',
    locale: 'en-US',
    content: 'Because nums[0] + nums[1] == 9, we return [0, 1].',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-two-sum-1',
    field_name: 'explanation',
    locale: 'zh-CN',
    content: '因为 nums[0] + nums[1] == 9，返回 [0, 1]。',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-two-sum-2',
    field_name: 'explanation',
    locale: 'en-US',
    content: 'nums[1] + nums[2] == 6, so we return [1, 2].',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-two-sum-2',
    field_name: 'explanation',
    locale: 'zh-CN',
    content: 'nums[1] + nums[2] == 6，返回 [1, 2]。',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-two-sum-3',
    field_name: 'explanation',
    locale: 'en-US',
    content:
      'The same element cannot be used twice, but two different elements with value 3 can be used.',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-two-sum-3',
    field_name: 'explanation',
    locale: 'zh-CN',
    content: '同一个元素不能使用两次，但两个值为 3 的不同元素可以使用。',
  },

  // Longest Substring Examples
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-longest-sub-1',
    field_name: 'explanation',
    locale: 'en-US',
    content: 'The answer is "abc", with the length of 3.',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-longest-sub-1',
    field_name: 'explanation',
    locale: 'zh-CN',
    content: '答案是 "abc"，长度为 3。',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-longest-sub-2',
    field_name: 'explanation',
    locale: 'en-US',
    content: 'The answer is "b", with the length of 1.',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-longest-sub-2',
    field_name: 'explanation',
    locale: 'zh-CN',
    content: '答案是 "b"，长度为 1。',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-longest-sub-3',
    field_name: 'explanation',
    locale: 'en-US',
    content:
      'The answer is "wke", with the length of 3. Note that the answer must be a substring, "pwke" is a subsequence and not a substring.',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-longest-sub-3',
    field_name: 'explanation',
    locale: 'zh-CN',
    content:
      '答案是 "wke"，长度为 3。请注意答案必须是子串，"pwke" 是子序列而不是子串。',
  },

  // Merge Intervals Examples
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-merge-1',
    field_name: 'explanation',
    locale: 'en-US',
    content: 'Intervals [1,3] and [2,6] overlap, merge into [1,6].',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-merge-1',
    field_name: 'explanation',
    locale: 'zh-CN',
    content: '区间 [1,3] 和 [2,6] 重叠，合并为 [1,6]。',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-merge-2',
    field_name: 'explanation',
    locale: 'en-US',
    content: 'Intervals that touch at the boundary are merged.',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-merge-2',
    field_name: 'explanation',
    locale: 'zh-CN',
    content: '边界相接的区间也会被合并。',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-merge-3',
    field_name: 'explanation',
    locale: 'en-US',
    content: 'The second interval is contained within the first.',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-merge-3',
    field_name: 'explanation',
    locale: 'zh-CN',
    content: '第二个区间被包含在第一个区间内。',
  },

  // Median Examples
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-median-1',
    field_name: 'explanation',
    locale: 'en-US',
    content: 'Merged array is [1,2,3] and median is 2.',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-median-1',
    field_name: 'explanation',
    locale: 'zh-CN',
    content: '合并后数组为 [1,2,3]，中位数是 2。',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-median-2',
    field_name: 'explanation',
    locale: 'en-US',
    content: 'Merged array is [1,2,3,4] and median is (2 + 3) / 2 = 2.5.',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-median-2',
    field_name: 'explanation',
    locale: 'zh-CN',
    content: '合并后数组为 [1,2,3,4]，中位数是 (2 + 3) / 2 = 2.5。',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-median-3',
    field_name: 'explanation',
    locale: 'en-US',
    content: 'Median is the only element 1.',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-median-3',
    field_name: 'explanation',
    locale: 'zh-CN',
    content: '中位数是唯一的元素 1。',
  },

  // Number of Islands Examples
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-islands-1',
    field_name: 'explanation',
    locale: 'en-US',
    content: 'All land cells are connected into a single island.',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-islands-1',
    field_name: 'explanation',
    locale: 'zh-CN',
    content: '所有陆地单元格连成了一个岛屿。',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-islands-2',
    field_name: 'explanation',
    locale: 'en-US',
    content:
      'There is one island in the top-left, one in the middle, and one in the bottom-right.',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-islands-2',
    field_name: 'explanation',
    locale: 'zh-CN',
    content: '左上角有一个岛屿，中间有一个，右下角有一个。',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-islands-3',
    field_name: 'explanation',
    locale: 'en-US',
    content: 'No land cells, so zero islands.',
  },
  {
    entity_type: 'PROBLEM_EXAMPLE',
    entity_id: 'ex-islands-3',
    field_name: 'explanation',
    locale: 'zh-CN',
    content: '没有陆地单元格，所以岛屿数量为零。',
  },
];

// ============================================================================
// PROBLEM LIST TRANSLATIONS
// ============================================================================

export const problemListTranslations: TranslationSeed[] = [
  // Essential Problems
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-essentials',
    field_name: 'name',
    locale: 'en-US',
    content: 'Essential Problems',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-essentials',
    field_name: 'name',
    locale: 'zh-CN',
    content: '必刷题目',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-essentials',
    field_name: 'description',
    locale: 'en-US',
    content: 'The absolute must-know patterns.',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-essentials',
    field_name: 'description',
    locale: 'zh-CN',
    content: '绝对必须掌握的题目模式。',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-essentials',
    field_name: 'banner_tag',
    locale: 'en-US',
    content: 'Essential',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-essentials',
    field_name: 'banner_tag',
    locale: 'zh-CN',
    content: '必刷',
  },

  // Sliding Window Classics
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-sliding-window',
    field_name: 'name',
    locale: 'en-US',
    content: 'Sliding Window Classics',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-sliding-window',
    field_name: 'name',
    locale: 'zh-CN',
    content: '滑动窗口经典题',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-sliding-window',
    field_name: 'description',
    locale: 'en-US',
    content:
      'Strings and arrays that force you to manage window boundaries correctly.',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-sliding-window',
    field_name: 'description',
    locale: 'zh-CN',
    content: '需要正确管理窗口边界的字符串和数组问题。',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-sliding-window',
    field_name: 'banner_tag',
    locale: 'en-US',
    content: 'Pattern',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-sliding-window',
    field_name: 'banner_tag',
    locale: 'zh-CN',
    content: '模式',
  },

  // Intervals & Sorting
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-intervals',
    field_name: 'name',
    locale: 'en-US',
    content: 'Intervals & Sorting',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-intervals',
    field_name: 'name',
    locale: 'zh-CN',
    content: '区间与排序',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-intervals',
    field_name: 'description',
    locale: 'en-US',
    content: 'Sweep line, merging, and ordering exercises seen in contests.',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-intervals',
    field_name: 'description',
    locale: 'zh-CN',
    content: '比赛中常见的扫描线、区间合并和排序练习。',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-intervals',
    field_name: 'banner_tag',
    locale: 'en-US',
    content: 'Sorting',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-intervals',
    field_name: 'banner_tag',
    locale: 'zh-CN',
    content: '排序',
  },

  // Graph DFS/BFS Warm-up
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-graph-dfs',
    field_name: 'name',
    locale: 'en-US',
    content: 'Graph DFS/BFS Warm-up',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-graph-dfs',
    field_name: 'name',
    locale: 'zh-CN',
    content: '图遍历热身题',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-graph-dfs',
    field_name: 'description',
    locale: 'en-US',
    content: 'Quick traversal problems to drill grid and graph intuition.',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-graph-dfs',
    field_name: 'description',
    locale: 'zh-CN',
    content: '快速遍历问题，训练网格和图的直觉。',
  },

  // Hard Benchmarks
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-hard-bench',
    field_name: 'name',
    locale: 'en-US',
    content: 'Hard Benchmarks',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-hard-bench',
    field_name: 'name',
    locale: 'zh-CN',
    content: '困难题基准',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-hard-bench',
    field_name: 'description',
    locale: 'en-US',
    content: 'Curated hard problems for interview prep and contest training.',
  },
  {
    entity_type: 'PROBLEM_LIST',
    entity_id: 'list-hard-bench',
    field_name: 'description',
    locale: 'zh-CN',
    content: '精选困难题，用于面试准备和比赛训练。',
  },
];

// ============================================================================
// CONTEST TRANSLATIONS
// ============================================================================

export const contestTranslations: TranslationSeed[] = [
  // Weekly Contest 477
  {
    entity_type: 'CONTEST',
    entity_id: CONTEST_IDS.WEEKLY_477,
    field_name: 'title',
    locale: 'en-US',
    content: 'Weekly Contest 477',
  },
  {
    entity_type: 'CONTEST',
    entity_id: CONTEST_IDS.WEEKLY_477,
    field_name: 'title',
    locale: 'zh-CN',
    content: '第 477 场周赛',
  },
  {
    entity_type: 'CONTEST',
    entity_id: CONTEST_IDS.WEEKLY_477,
    field_name: 'description',
    locale: 'en-US',
    content: 'Join this weekly contest to test your skills.',
  },
  {
    entity_type: 'CONTEST',
    entity_id: CONTEST_IDS.WEEKLY_477,
    field_name: 'description',
    locale: 'zh-CN',
    content: '参加周赛，检验你的编程能力。',
  },

  // Weekly Contest 476
  {
    entity_type: 'CONTEST',
    entity_id: CONTEST_IDS.WEEKLY_476,
    field_name: 'title',
    locale: 'en-US',
    content: 'Weekly Contest 476',
  },
  {
    entity_type: 'CONTEST',
    entity_id: CONTEST_IDS.WEEKLY_476,
    field_name: 'title',
    locale: 'zh-CN',
    content: '第 476 场周赛',
  },
  {
    entity_type: 'CONTEST',
    entity_id: CONTEST_IDS.WEEKLY_476,
    field_name: 'description',
    locale: 'en-US',
    content: 'Previous weekly contest archive.',
  },
  {
    entity_type: 'CONTEST',
    entity_id: CONTEST_IDS.WEEKLY_476,
    field_name: 'description',
    locale: 'zh-CN',
    content: '往期周赛存档。',
  },

  // Biweekly Contest 170
  {
    entity_type: 'CONTEST',
    entity_id: CONTEST_IDS.BIWEEKLY_170,
    field_name: 'title',
    locale: 'en-US',
    content: 'Biweekly Contest 170',
  },
  {
    entity_type: 'CONTEST',
    entity_id: CONTEST_IDS.BIWEEKLY_170,
    field_name: 'title',
    locale: 'zh-CN',
    content: '第 170 场双周赛',
  },
  {
    entity_type: 'CONTEST',
    entity_id: CONTEST_IDS.BIWEEKLY_170,
    field_name: 'description',
    locale: 'en-US',
    content: 'Biweekly contest with increasing difficulty.',
  },
  {
    entity_type: 'CONTEST',
    entity_id: CONTEST_IDS.BIWEEKLY_170,
    field_name: 'description',
    locale: 'zh-CN',
    content: '难度递增的双周赛。',
  },
];

// ============================================================================
// SUBMISSION STATUS TRANSLATIONS
// ============================================================================

export const submissionStatusTranslations: TranslationSeed[] = [
  // Accepted
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Accepted',
    field_name: 'label',
    locale: 'en-US',
    content: 'Accepted',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Accepted',
    field_name: 'label',
    locale: 'zh-CN',
    content: '通过',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Accepted',
    field_name: 'description',
    locale: 'en-US',
    content: 'All test cases passed.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Accepted',
    field_name: 'description',
    locale: 'zh-CN',
    content: '所有测试用例通过。',
  },

  // Wrong Answer
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Wrong Answer',
    field_name: 'label',
    locale: 'en-US',
    content: 'Wrong Answer',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Wrong Answer',
    field_name: 'label',
    locale: 'zh-CN',
    content: '答案错误',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Wrong Answer',
    field_name: 'description',
    locale: 'en-US',
    content: 'Output does not match the expected result.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Wrong Answer',
    field_name: 'description',
    locale: 'zh-CN',
    content: '输出结果与预期不符。',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Wrong Answer',
    field_name: 'suggestion',
    locale: 'en-US',
    content: 'Review edge cases, input parsing, and output formatting.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Wrong Answer',
    field_name: 'suggestion',
    locale: 'zh-CN',
    content: '检查边界情况、输入解析和输出格式。',
  },

  // Time Limit Exceeded
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Time Limit Exceeded',
    field_name: 'label',
    locale: 'en-US',
    content: 'Time Limit Exceeded',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Time Limit Exceeded',
    field_name: 'label',
    locale: 'zh-CN',
    content: '超出时间限制',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Time Limit Exceeded',
    field_name: 'description',
    locale: 'en-US',
    content: 'Execution time exceeded the allowed limit.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Time Limit Exceeded',
    field_name: 'description',
    locale: 'zh-CN',
    content: '执行时间超出允许限制。',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Time Limit Exceeded',
    field_name: 'suggestion',
    locale: 'en-US',
    content: 'Consider a more efficient algorithm or optimize data structures.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Time Limit Exceeded',
    field_name: 'suggestion',
    locale: 'zh-CN',
    content: '考虑使用更高效的算法或优化数据结构。',
  },

  // Memory Limit Exceeded
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Memory Limit Exceeded',
    field_name: 'label',
    locale: 'en-US',
    content: 'Memory Limit Exceeded',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Memory Limit Exceeded',
    field_name: 'label',
    locale: 'zh-CN',
    content: '超出内存限制',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Memory Limit Exceeded',
    field_name: 'description',
    locale: 'en-US',
    content: 'Memory usage exceeded the allowed limit.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Memory Limit Exceeded',
    field_name: 'description',
    locale: 'zh-CN',
    content: '内存使用超出允许限制。',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Memory Limit Exceeded',
    field_name: 'suggestion',
    locale: 'en-US',
    content: 'Reduce memory usage by optimizing data structures or using iterative approaches.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Memory Limit Exceeded',
    field_name: 'suggestion',
    locale: 'zh-CN',
    content: '通过优化数据结构或使用迭代方法减少内存使用。',
  },

  // Runtime Error
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Runtime Error',
    field_name: 'label',
    locale: 'en-US',
    content: 'Runtime Error',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Runtime Error',
    field_name: 'label',
    locale: 'zh-CN',
    content: '运行时错误',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Runtime Error',
    field_name: 'description',
    locale: 'en-US',
    content: 'Program crashed during execution.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Runtime Error',
    field_name: 'description',
    locale: 'zh-CN',
    content: '程序在执行过程中崩溃。',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Runtime Error',
    field_name: 'suggestion',
    locale: 'en-US',
    content: 'Check for null pointers, array bounds, and division by zero.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Runtime Error',
    field_name: 'suggestion',
    locale: 'zh-CN',
    content: '检查空指针、数组越界和除零错误。',
  },

  // Compile Error
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Compile Error',
    field_name: 'label',
    locale: 'en-US',
    content: 'Compile Error',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Compile Error',
    field_name: 'label',
    locale: 'zh-CN',
    content: '编译错误',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Compile Error',
    field_name: 'description',
    locale: 'en-US',
    content: 'Code failed to compile.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Compile Error',
    field_name: 'description',
    locale: 'zh-CN',
    content: '代码编译失败。',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Compile Error',
    field_name: 'suggestion',
    locale: 'en-US',
    content: 'Fix syntax errors shown in the compiler output.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Compile Error',
    field_name: 'suggestion',
    locale: 'zh-CN',
    content: '修复编译器输出中显示的语法错误。',
  },

  // Output Limit Exceeded
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Output Limit Exceeded',
    field_name: 'label',
    locale: 'en-US',
    content: 'Output Limit Exceeded',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Output Limit Exceeded',
    field_name: 'label',
    locale: 'zh-CN',
    content: '超出输出限制',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Output Limit Exceeded',
    field_name: 'description',
    locale: 'en-US',
    content: 'Program produced too much output.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Output Limit Exceeded',
    field_name: 'description',
    locale: 'zh-CN',
    content: '程序产生了过多输出。',
  },

  // Presentation Error
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Presentation Error',
    field_name: 'label',
    locale: 'en-US',
    content: 'Presentation Error',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Presentation Error',
    field_name: 'label',
    locale: 'zh-CN',
    content: '格式错误',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Presentation Error',
    field_name: 'description',
    locale: 'en-US',
    content: 'Output format does not match the expected format.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Presentation Error',
    field_name: 'description',
    locale: 'zh-CN',
    content: '输出格式与预期格式不符。',
  },

  // System Error
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'System Error',
    field_name: 'label',
    locale: 'en-US',
    content: 'System Error',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'System Error',
    field_name: 'label',
    locale: 'zh-CN',
    content: '系统错误',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'System Error',
    field_name: 'description',
    locale: 'en-US',
    content: 'An internal error occurred during judging.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'System Error',
    field_name: 'description',
    locale: 'zh-CN',
    content: '评测过程中发生内部错误。',
  },

  // Judging
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Judging',
    field_name: 'label',
    locale: 'en-US',
    content: 'Judging',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Judging',
    field_name: 'label',
    locale: 'zh-CN',
    content: '评测中',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Judging',
    field_name: 'description',
    locale: 'en-US',
    content: 'Your submission is currently being judged.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Judging',
    field_name: 'description',
    locale: 'zh-CN',
    content: '你的提交正在评测中。',
  },

  // Pending
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Pending',
    field_name: 'label',
    locale: 'en-US',
    content: 'Pending',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Pending',
    field_name: 'label',
    locale: 'zh-CN',
    content: '等待中',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Pending',
    field_name: 'description',
    locale: 'en-US',
    content: 'Your submission is waiting in the queue.',
  },
  {
    entity_type: 'SUBMISSION_STATUS',
    entity_id: 'Pending',
    field_name: 'description',
    locale: 'zh-CN',
    content: '你的提交正在队列中等待。',
  },
];

// ============================================================================
// EXPORT ALL TRANSLATIONS
// ============================================================================

export const allTranslations: TranslationSeed[] = [
  ...problemTranslations,
  ...problemDetailTranslations,
  ...problemTagTranslations,
  ...problemExampleTranslations,
  ...problemListTranslations,
  ...contestTranslations,
  ...submissionStatusTranslations,
];

export default allTranslations;
