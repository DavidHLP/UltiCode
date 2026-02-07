import { EdgeOperationTargetType } from '@prisma/client';

/**
 * 评论实体类型枚举
 * 用于区分不同类型的评论
 */
export enum CommentEntityType {
  FORUM = 'FORUM',
  SOLUTION = 'SOLUTION',
}

/**
 * 基础评论接口
 * 定义所有评论类型共有的字段
 */
export interface BaseComment {
  id: string;
  content: string;
  parentId: string | null;
  authorId: string;
  createdAt: Date;
  editedAt: Date | null;
}

/**
 * 带投票信息的评论接口
 * 扩展基础评论，添加投票相关字段
 */
export interface CommentWithVotes {
  likes: number;
  dislikes: number;
  upvotes: number;
  userVote: number;
}

/**
 * 评论作者信息
 */
export interface CommentAuthor {
  id: string;
  username: string;
  avatar: string | null;
}

/**
 * 完整评论响应（包含作者和投票信息）
 */
export interface CommentResponse extends BaseComment, CommentWithVotes {
  author: CommentAuthor;
}

/**
 * 投票统计信息
 */
export interface VoteStats {
  likes: number;
  dislikes: number;
}

/**
 * 评论实体 ID 映射
 * 用于在不同评论类型间映射 ID 字段
 */
export interface CommentIdMapping {
  forumComment: string;
  solutionComment: string;
}

/**
 * 评论类型到投票目标类型的映射
 */
export const COMMENT_TYPE_TO_VOTE_TARGET: Record<
  CommentEntityType,
  EdgeOperationTargetType
> = {
  [CommentEntityType.FORUM]: EdgeOperationTargetType.FORUM_COMMENT,
  [CommentEntityType.SOLUTION]: EdgeOperationTargetType.SOLUTION_COMMENT,
} as const;

/**
 * 获取评论类型对应的投票目标类型
 */
export function getVoteTargetType(
  type: CommentEntityType,
): EdgeOperationTargetType {
  return COMMENT_TYPE_TO_VOTE_TARGET[type];
}
