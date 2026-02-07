import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { VoteService } from '../../vote/vote.service';
import {
  CommentEntityType,
  CommentWithVotes,
  VoteStats,
  getVoteTargetType,
} from '../types/comment.types';

/**
 * BaseCommentService - 评论系统基类
 *
 * 提供评论管理的通用功能：
 * - 投票增强（批量获取投票统计和用户投票状态）
 * - 权限验证（评论所有权检查）
 * - 类型映射（评论类型到投票目标类型）
 *
 * 子类应继承此类并实现特定业务逻辑
 */
export abstract class BaseCommentService {
  constructor(
    protected readonly prisma: PrismaService,
    protected readonly voteService: VoteService,
  ) {}

  /**
   * 为评论列表批量添加投票信息
   *
   * @param comments - 评论列表
   * @param type - 评论实体类型
   * @param userId - 当前用户 ID（可选）
   * @returns 带投票信息的评论列表
   */
  protected async enrichWithVotes<T extends { id: string }>(
    comments: T[],
    type: CommentEntityType,
    userId?: string,
  ): Promise<(T & CommentWithVotes)[]> {
    if (comments.length === 0) return [];

    const commentIds = comments.map((c) => c.id);
    const targetType = getVoteTargetType(type);

    // 并行获取投票统计和用户投票状态
    const [voteStatsMap, userVoteMap] = await Promise.all([
      this.voteService.getVoteCountsBatch(targetType, commentIds),
      userId
        ? this.voteService.getUserVotesBatch(userId, targetType, commentIds)
        : Promise.resolve(new Map<string, number>()),
    ]);

    // 组合数据
    return comments.map((comment) => {
      const stats = voteStatsMap.get(comment.id) || { likes: 0, dislikes: 0 };
      const userVote = userVoteMap.get(comment.id) || 0;

      return {
        ...comment,
        likes: stats.likes,
        dislikes: stats.dislikes,
        upvotes: stats.likes,
        userVote,
      } as T & CommentWithVotes;
    });
  }

  /**
   * 获取单个评论的投票统计
   *
   * @param commentId - 评论 ID
   * @param type - 评论实体类型
   * @returns 投票统计信息
   */
  protected async getVoteStats(
    commentId: string,
    type: CommentEntityType,
  ): Promise<VoteStats> {
    const targetType = getVoteTargetType(type);
    return this.voteService.getVoteCounts(targetType, commentId);
  }

  /**
   * 获取用户对评论的投票状态
   *
   * @param userId - 用户 ID
   * @param commentId - 评论 ID
   * @param type - 评论实体类型
   * @returns 投票值 (1, -1, 0)
   */
  protected async getUserVote(
    userId: string,
    commentId: string,
    type: CommentEntityType,
  ): Promise<number> {
    const targetType = getVoteTargetType(type);
    return this.voteService.getUserVote(userId, targetType, commentId);
  }

  /**
   * 验证评论所有权
   *
   * @param commentId - 评论 ID
   * @param userId - 用户 ID
   * @param type - 评论实体类型
   * @param authorIdField - 作者 ID 字段名（默认为 'author_id'）
   * @throws NotFoundException - 评论不存在
   * @throws ForbiddenException - 用户不是评论作者
   */
  protected async validateCommentOwnership(
    commentId: string,
    userId: string,
    type: CommentEntityType,
    authorIdField: 'author_id' | 'user_id' = 'author_id',
  ): Promise<void> {
    const model =
      type === CommentEntityType.FORUM ? 'forumComment' : 'solutionComment';

    const comment = await (
      this.prisma[model] as unknown as {
        findUnique: (args: {
          where: { id: string };
          select: { [key: string]: true };
        }) => Promise<{ [key: string]: string } | null>;
      }
    ).findUnique({
      where: { id: commentId },
      select: { [authorIdField]: true },
    });

    if (!comment) {
      throw new NotFoundException('Comment not found');
    }

    if (comment[authorIdField] !== userId) {
      throw new ForbiddenException('Not allowed to modify this comment');
    }
  }

  /**
   * 获取评论类型对应的投票目标类型
   *
   * @param type - 评论实体类型
   * @returns 投票目标类型
   */
  protected getVoteTargetType(
    type: CommentEntityType,
  ): import('@prisma/client').EdgeOperationTargetType {
    return getVoteTargetType(type);
  }
}
