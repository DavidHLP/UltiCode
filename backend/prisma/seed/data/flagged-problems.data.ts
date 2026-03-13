// prisma/seed/data/flagged-problems.data.ts
import { PROBLEM_IDS } from './problems.data';
import { USER_IDS } from './users.data';

/**
 * Flag status types:
 * - PENDING: Newly flagged, awaiting review
 * - REVIEWED: Moderation in progress
 * - RESOLVED: Issue fixed/confirmed
 * - DISMISSED: False positive/no action needed
 */

export interface FlaggedProblemData {
  problem_id: number;
  flag_reason: string;
  flag_status: 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED';
  flag_reported_by: string;
  flag_reported_at: Date;
  flag_notes: string | null;
  flag_reviewed_by: string | null;
  flag_reviewed_at: Date | null;
}

/**
 * Flagged problems seed data for moderation queue testing.
 *
 * Note: Some problems appear multiple times with different flags to test
 * the "most recent flag wins" behavior in the moderation system.
 * The final state depends on the array order (last update wins).
 */
export const FLAGGED_PROBLEM_DATA: readonly FlaggedProblemData[] = [
  // === PENDING - 等待审核 ===
  {
    problem_id: PROBLEM_IDS.LONGEST_SUBSTRING,
    flag_reason: `题目描述存在歧义。Example 1 中的输入 "abcabcbb" 应该返回 3，但部分用户认为应该是 4，因为 "abc" 可以出现在不同位置。建议在题目描述中明确说明是找最长不重复子串的长度，而不是所有不重复子串的数量。`,
    flag_status: 'PENDING',
    flag_reported_by: USER_IDS.YUKI,
    flag_reported_at: new Date('2025-03-10T08:30:00Z'),
    flag_notes: null,
    flag_reviewed_by: null,
    flag_reviewed_at: null,
  },
  {
    problem_id: PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS,
    flag_reason: `时间限制过于严格。当前 O(log(m+n)) 的解法在极端情况下（如两个数组长度差异很大时）会超时。建议将时间限制从 100ms 调整到 200ms，或者添加更多测试用例来验证边界情况。`,
    flag_status: 'PENDING',
    flag_reported_by: USER_IDS.CHEN,
    flag_reported_at: new Date('2025-03-12T14:15:00Z'),
    flag_notes: null,
    flag_reviewed_by: null,
    flag_reviewed_at: null,
  },
  {
    problem_id: PROBLEM_IDS.NUMBER_OF_ISLANDS,
    flag_reason: `测试用例不完整。当前测试用例没有覆盖单个格子的情况（1x1 网格）。当网格只有一个元素 '1' 时，应该返回 1；当只有一个元素 '0' 时，应该返回 0。建议添加这些边界测试用例。`,
    flag_status: 'PENDING',
    flag_reported_by: USER_IDS.MAX,
    flag_reported_at: new Date('2025-03-13T09:45:00Z'),
    flag_notes: null,
    flag_reviewed_by: null,
    flag_reviewed_at: null,
  },

  // === REVIEWED - 审核中 ===
  {
    problem_id: PROBLEM_IDS.MERGE_INTERVALS,
    flag_reason: `题目约束条件缺失。当前题目没有明确说明输入区间是否已经按起始时间排序。部分提交者假设输入已排序，导致排序的 O(n log n) 步骤被跳过，但官方解答包含排序步骤。建议在 Constraints 中明确说明输入顺序。`,
    flag_status: 'REVIEWED',
    flag_reported_by: USER_IDS.SARA,
    flag_reported_at: new Date('2025-03-05T11:20:00Z'),
    flag_notes: `已确认问题存在。正在与题目维护者协商修改 Constraints 部分。预计本周内完成更新。临时添加了 Hints 说明输入可能未排序。`,
    flag_reviewed_by: USER_IDS.ALEX,
    flag_reviewed_at: new Date('2025-03-08T16:30:00Z'),
  },
  {
    problem_id: PROBLEM_IDS.TWO_SUM,
    flag_reason: `官方题解代码存在潜在 bug。在 Python 实现中，当输入包含重复元素时（如 [3, 3], target = 6），哈希表的覆盖逻辑可能导致错误结果。虽然在当前测试用例下不会触发，但代码风格不够健壮。`,
    flag_status: 'REVIEWED',
    flag_reported_by: USER_IDS.EMMA,
    flag_reported_at: new Date('2025-03-06T10:00:00Z'),
    flag_notes: `经核实，Python 官方题解使用 enumerate 遍历，先检查再添加，不会出现覆盖问题。但确实容易让人误解，建议添加代码注释说明遍历顺序的重要性。`,
    flag_reviewed_by: USER_IDS.DAVID,
    flag_reviewed_at: new Date('2025-03-09T14:00:00Z'),
  },

  // === RESOLVED - 已解决 ===
  {
    problem_id: PROBLEM_IDS.COMBINE_TWO_TABLES,
    flag_reason: `SQL 题目的表结构与实际测试数据库不一致。题目描述中 Person 表的 PersonId 字段在某些测试用例中被称为 person_id（小写），导致 SQL 语句在某些数据库配置下执行失败。`,
    flag_status: 'RESOLVED',
    flag_reported_by: USER_IDS.KEVIN,
    flag_reported_at: new Date('2025-02-28T13:45:00Z'),
    flag_notes: `问题已确认并修复。统一使用 PersonId 作为字段名。已更新所有测试用例的数据库 schema。感谢报告！`,
    flag_reviewed_by: USER_IDS.TOM,
    flag_reviewed_at: new Date('2025-03-02T09:30:00Z'),
  },
  {
    problem_id: PROBLEM_IDS.TENTH_LINE,
    flag_reason: `Shell 题目的预期输出格式不明确。当文件不足 10 行时，部分用户期望输出空行，而实际上应该不输出任何内容。建议在题目描述中明确说明这种情况的处理方式。`,
    flag_status: 'RESOLVED',
    flag_reported_by: USER_IDS.LILY,
    flag_reported_at: new Date('2025-02-25T15:30:00Z'),
    flag_notes: `已更新题目描述，明确说明"如果文件少于 10 行，不输出任何内容"。同时添加了对应的边界测试用例。问题已解决。`,
    flag_reviewed_by: USER_IDS.SHADCN,
    flag_reviewed_at: new Date('2025-02-27T11:00:00Z'),
  },

  // === DISMISSED - 已驳回 ===
  {
    problem_id: PROBLEM_IDS.PRINT_FOOBAR_ALTERNATELY,
    flag_reason: `题目难度标记为 Medium 不合理。这应该是一道 Easy 题目，因为只需要使用简单的信号量或条件变量即可解决。建议降低难度等级以更准确反映题目难度。`,
    flag_status: 'DISMISSED',
    flag_reported_by: USER_IDS.TOURIST,
    flag_reported_at: new Date('2025-02-20T08:00:00Z'),
    flag_notes: `经评估，并发题目对于大多数开发者来说确实具有挑战性。题目难度综合考虑了多因素（概念理解、正确性、边界情况处理等），维持 Medium 评级。对于熟悉并发的开发者可能感觉偏简单，但对于初学者来说难度适中。`,
    flag_reviewed_by: USER_IDS.STACK_UNWIND,
    flag_reviewed_at: new Date('2025-02-22T10:15:00Z'),
  },
  {
    problem_id: PROBLEM_IDS.NUMBER_OF_ISLANDS,
    flag_reason: `重复举报：这道题与 "Max Area of Island" 过于相似，建议下架或合并。两道题的核心算法完全相同，只是统计目标不同（数量 vs 面积）。`,
    flag_status: 'DISMISSED',
    flag_reported_by: USER_IDS.JIANGLY,
    flag_reported_at: new Date('2025-02-18T06:30:00Z'),
    flag_notes: `虽然两道题使用相似的算法（DFS/BFS/Union-Find），但它们考察的侧重点不同：Number of Islands 考察连通分量的计数，Max Area of Island 考察最大连通分量的大小。两者都有独立的学习价值，不建议合并。`,
    flag_reviewed_by: USER_IDS.BENQ,
    flag_reviewed_at: new Date('2025-02-20T14:45:00Z'),
  },

  // === 更多 PENDING 状态，增加数据多样性 ===
  {
    problem_id: PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS,
    flag_reason: `内存限制过于严格。归并排序的 O(m+n) 空间复杂度解法在某些测试用例下会超出内存限制（16MB）。建议将内存限制提高到 32MB，或者在题目中明确禁止使用归并方法。`,
    flag_status: 'PENDING',
    flag_reported_by: USER_IDS.ECNERWALA,
    flag_reported_at: new Date('2025-03-14T02:00:00Z'),
    flag_notes: null,
    flag_reviewed_by: null,
    flag_reviewed_at: null,
  },
  {
    problem_id: PROBLEM_IDS.LONGEST_SUBSTRING,
    flag_reason: `Java 语言的 Starter Code 使用了过时的写法。建议使用更现代的 Java 8+ Stream API 或 StringBuilder 来提高代码可读性。`,
    flag_status: 'PENDING',
    flag_reported_by: USER_IDS.UM_NIK,
    flag_reported_at: new Date('2025-03-13T18:20:00Z'),
    flag_notes: null,
    flag_reviewed_by: null,
    flag_reviewed_at: null,
  },
] as const;

export default FLAGGED_PROBLEM_DATA;
