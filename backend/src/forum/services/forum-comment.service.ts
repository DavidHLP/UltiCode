import {
  Injectable,
  ForbiddenException,
  NotFoundException,
} from '@nestjs/common';
import { randomUUID } from 'crypto';
import { PrismaService } from '../../prisma.service';
import { VoteService } from '../../vote/vote.service';
import { EdgeOperationTargetType, Prisma } from '@prisma/client';
import type { ForumComment, ForumUser, PrismaClient } from '../types';
import { convertCommentToTypeOrmFormat } from '../types';

/**
 * ForumCommentService - 评论管理和嵌套评论
 *
 * 职责:
 * - 创建、更新、删除评论
 * - 获取评论树（thread）
 * - 批量获取评论数
 */
@Injectable()
export class ForumCommentService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly voteService: VoteService,
  ) {}

  /**
   * 批量获取帖子的评论数
   */
  async getCommentCounts(postIds: string[]): Promise<Map<string, number>> {
    if (postIds.length === 0) return new Map<string, number>();

    const counts = await this.prisma.forumComment.groupBy({
      by: ['post_id'],
      where: { post_id: { in: postIds } },
      _count: { id: true },
    });

    const countMap = new Map<string, number>();
    counts.forEach((item) => {
      countMap.set(item.post_id, item._count.id);
    });
    return countMap;
  }

  /**
   * 创建评论
   */
  async createComment(
    postId: string,
    body: string,
    parentId: string | null,
    author: { id: string; username: string; avatar?: string | null },
    moderationService: {
      ensureForumUser: (
        user: { id: string; username: string; avatar?: string | null },
        tx?: PrismaClient,
      ) => Promise<ForumUser>;
    },
  ): Promise<ForumComment> {
    const post = await this.prisma.forumPost.findUnique({
      where: { id: postId },
    });
    if (!post) {
      throw new NotFoundException('Post not found');
    }

    await moderationService.ensureForumUser(author);

    const comment = await this.prisma.forumComment.create({
      data: {
        id: randomUUID(),
        post_id: postId,
        body,
        parent_id: parentId,
        author_id: author.id,
        created_at: new Date(),
      },
      include: { author: true },
    });

    // 更新帖子的评论计数
    const commentCount = await this.prisma.forumComment.count({
      where: { post_id: postId },
    });
    const stats =
      (post.stats as {
        shares?: number;
        views?: number;
        comments?: number;
        saves?: number;
      }) || {};
    stats.comments = commentCount;
    await this.prisma.forumPost.update({
      where: { id: postId },
      data: { stats: stats as Prisma.InputJsonValue },
    });

    return convertCommentToTypeOrmFormat(comment);
  }

  /**
   * 更新评论
   */
  async updateComment(
    commentId: string,
    body: string,
    userId: string,
  ): Promise<ForumComment> {
    const comment = await this.prisma.forumComment.findUnique({
      where: { id: commentId },
      include: { author: true },
    });
    if (!comment) {
      throw new NotFoundException('Comment not found');
    }
    if (comment.author_id !== userId) {
      throw new ForbiddenException('Not allowed to edit this comment');
    }

    const updated = await this.prisma.forumComment.update({
      where: { id: commentId },
      data: {
        body,
        edited_at: new Date(),
      },
      include: { author: true },
    });

    return convertCommentToTypeOrmFormat(updated);
  }

  /**
   * 删除评论
   */
  async deleteComment(commentId: string, userId: string): Promise<void> {
    const comment = await this.prisma.forumComment.findUnique({
      where: { id: commentId },
    });
    if (!comment) {
      throw new NotFoundException('Comment not found');
    }
    if (comment.author_id !== userId) {
      throw new ForbiddenException('Not allowed to delete this comment');
    }

    await this.prisma.forumComment.delete({
      where: { id: commentId },
    });

    // 更新帖子的评论计数
    const post = await this.prisma.forumPost.findUnique({
      where: { id: comment.post_id },
    });
    if (post) {
      const commentCount = await this.prisma.forumComment.count({
        where: { post_id: comment.post_id },
      });
      const stats =
        (post.stats as {
          shares?: number;
          views?: number;
          comments?: number;
          saves?: number;
        }) || {};
      stats.comments = commentCount;
      await this.prisma.forumPost.update({
        where: { id: comment.post_id },
        data: { stats: stats as Prisma.InputJsonValue },
      });
    }
  }

  /**
   * 获取帖子的评论树（带投票信息）
   */
  async getThread(postId: string, userId?: string): Promise<ForumComment[]> {
    const comments = await this.prisma.forumComment.findMany({
      where: { post_id: postId },
      include: { author: true },
      orderBy: { created_at: 'asc' },
    });

    // 获取评论投票统计
    const commentIds = comments.map((c) => c.id);
    const commentVoteMap = await this.voteService.getVoteCountsBatch(
      EdgeOperationTargetType.FORUM_COMMENT,
      commentIds,
    );

    // 获取用户投票状态
    let commentUserVoteMap = new Map<string, number>();
    if (userId) {
      commentUserVoteMap = await this.voteService.getUserVotesBatch(
        userId,
        EdgeOperationTargetType.FORUM_COMMENT,
        commentIds,
      );
    }

    // 组合数据
    return comments.map((comment) => {
      const stats = commentVoteMap.get(comment.id) || {
        likes: 0,
        dislikes: 0,
      };
      const converted = convertCommentToTypeOrmFormat(comment);
      return {
        ...converted,
        likes: stats.likes,
        dislikes: stats.dislikes,
        upvotes: stats.likes,
        userVote: commentUserVoteMap.get(comment.id) || 0,
      };
    });
  }
}
