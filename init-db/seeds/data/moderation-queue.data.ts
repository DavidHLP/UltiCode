// prisma/seed/data/moderation-queue.data.ts
import { PROBLEM_IDS } from './problems.data';
import { USER_IDS } from './users.data';
import { SOLUTION_IDS } from './solutions.data';

/**
 * ModerationQueue entity types:
 * - forum_post: 论坛帖子
 * - forum_comment: 论坛评论
 * - solution: 题解
 * - solution_comment: 题解评论
 * - problem: 题目
 *
 * Note: Each entity_type + entity_id combination must be unique.
 */

export interface ModerationQueueData {
  id: string;
  entity_type: string;
  entity_id: string;
  author_id: string;
  priority: number;
  status: 'PENDING' | 'UNDER_REVIEW' | 'RESOLVED' | 'DISMISSED' | 'APPEAL_PENDING';
  report_count: number;
  primary_category: string;
  assigned_to_id?: string;
}

/**
 * Moderation queue seed data - 18 items for testing
 * Each entity_type + entity_id combination is unique.
 */
export const MODERATION_QUEUE_DATA: readonly ModerationQueueData[] = [
  // === PENDING - 待审核 (10 items) ===
  {
    id: 'mq-001',
    entity_type: 'forum_post',
    entity_id: 'post-contest-tilt',
    author_id: USER_IDS.DAVID,
    priority: 3,
    status: 'PENDING',
    report_count: 5,
    primary_category: 'SPAM',
  },
  {
    id: 'mq-002',
    entity_type: 'forum_post',
    entity_id: 'post-rust-hashmap',
    author_id: USER_IDS.STACK_UNWIND,
    priority: 2,
    status: 'PENDING',
    report_count: 3,
    primary_category: 'MISINFORMATION',
  },
  {
    id: 'mq-003',
    entity_type: 'forum_post',
    entity_id: 'post-segtree-visual',
    author_id: USER_IDS.TOURIST,
    priority: 1,
    status: 'PENDING',
    report_count: 2,
    primary_category: 'HARASSMENT',
  },
  {
    id: 'mq-004',
    entity_type: 'problem',
    entity_id: String(PROBLEM_IDS.LONGEST_SUBSTRING),
    author_id: USER_IDS.MAX,
    priority: 5,
    status: 'PENDING',
    report_count: 10,
    primary_category: 'WRONG_ANSWER',
  },
  {
    id: 'mq-005',
    entity_type: 'problem',
    entity_id: String(PROBLEM_IDS.MERGE_INTERVALS),
    author_id: USER_IDS.SARA,
    priority: 4,
    status: 'PENDING',
    report_count: 8,
    primary_category: 'COPYRIGHT',
  },
  {
    id: 'mq-006',
    entity_type: 'problem',
    entity_id: String(PROBLEM_IDS.NUMBER_OF_ISLANDS),
    author_id: USER_IDS.TOM,
    priority: 2,
    status: 'PENDING',
    report_count: 3,
    primary_category: 'WRONG_ANSWER',
  },
  {
    id: 'mq-007',
    entity_type: 'solution',
    entity_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
    author_id: USER_IDS.YUKI,
    priority: 3,
    status: 'PENDING',
    report_count: 4,
    primary_category: 'WRONG_ANSWER',
  },
  {
    id: 'mq-008',
    entity_type: 'forum_comment',
    entity_id: 'c-rust-1',
    author_id: USER_IDS.BENQ,
    priority: 2,
    status: 'PENDING',
    report_count: 3,
    primary_category: 'HARASSMENT',
  },
  {
    id: 'mq-009',
    entity_type: 'forum_comment',
    entity_id: 'c-rust-2',
    author_id: USER_IDS.STACK_UNWIND,
    priority: 1,
    status: 'PENDING',
    report_count: 1,
    primary_category: 'SPAM',
  },
  {
    id: 'mq-010',
    entity_type: 'forum_comment',
    entity_id: 'c-rust-3',
    author_id: USER_IDS.PETR,
    priority: 3,
    status: 'PENDING',
    report_count: 5,
    primary_category: 'HATE_SPEECH',
  },

  // === UNDER_REVIEW - 审核中 (3 items) ===
  {
    id: 'mq-011',
    entity_type: 'problem',
    entity_id: String(PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS),
    author_id: USER_IDS.DAVID,
    priority: 3,
    status: 'UNDER_REVIEW',
    report_count: 4,
    primary_category: 'OTHER',
    assigned_to_id: USER_IDS.SHADCN,
  },
  {
    id: 'mq-012',
    entity_type: 'solution',
    entity_id: SOLUTION_IDS.TWO_SUM_BRUTE,
    author_id: USER_IDS.ALEX,
    priority: 2,
    status: 'UNDER_REVIEW',
    report_count: 2,
    primary_category: 'SPAM',
    assigned_to_id: USER_IDS.SHADCN,
  },
  {
    id: 'mq-013',
    entity_type: 'solution',
    entity_id: SOLUTION_IDS.TWO_SUM_CPP,
    author_id: USER_IDS.EMMA,
    priority: 4,
    status: 'UNDER_REVIEW',
    report_count: 6,
    primary_category: 'COPYRIGHT',
    assigned_to_id: USER_IDS.SHADCN,
  },

  // === RESOLVED - 已解决 (2 items) ===
  {
    id: 'mq-014',
    entity_type: 'problem',
    entity_id: String(PROBLEM_IDS.COMBINE_TWO_TABLES),
    author_id: USER_IDS.KEVIN,
    priority: 2,
    status: 'RESOLVED',
    report_count: 2,
    primary_category: 'SPAM',
  },
  {
    id: 'mq-015',
    entity_type: 'solution',
    entity_id: SOLUTION_IDS.LONGEST_SUBSTR_SLIDING,
    author_id: USER_IDS.CHEN,
    priority: 3,
    status: 'RESOLVED',
    report_count: 4,
    primary_category: 'WRONG_ANSWER',
  },

  // === DISMISSED - 已驳回 (2 items) ===
  {
    id: 'mq-016',
    entity_type: 'forum_comment',
    entity_id: 'c-rust-4',
    author_id: USER_IDS.YUKI,
    priority: 1,
    status: 'DISMISSED',
    report_count: 1,
    primary_category: 'OTHER',
  },
  {
    id: 'mq-017',
    entity_type: 'problem',
    entity_id: String(PROBLEM_IDS.TENTH_LINE),
    author_id: USER_IDS.SARA,
    priority: 1,
    status: 'DISMISSED',
    report_count: 1,
    primary_category: 'OTHER',
  },

  // === APPEAL_PENDING - 申诉中 (1 item) ===
  {
    id: 'mq-018',
    entity_type: 'solution',
    entity_id: SOLUTION_IDS.MERGE_INTERVALS_SORT,
    author_id: USER_IDS.TOM,
    priority: 3,
    status: 'APPEAL_PENDING',
    report_count: 4,
    primary_category: 'WRONG_ANSWER',
  },
] as const;

export default MODERATION_QUEUE_DATA;
