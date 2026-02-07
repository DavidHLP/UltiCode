import { Injectable, NotFoundException } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { PrismaService } from '../../prisma.service';
import { VoteService } from '../../vote/vote.service';
import { BaseCommentService } from '../../common/services/base-comment.service';
import { CommentEntityType } from '../../common/types/comment.types';
import type { ForumComment, ForumUser, PrismaClient } from '../types';
import { convertCommentToTypeOrmFormat } from '../types';

/**
 * ForumCommentService - 评论管理和嵌套评论
 *
 * 职责:
 * - 创建、更新、删除评论
 * - 获取评论树（thread）
 * - 批量获取评论数
 *
 * 继承自 BaseCommentService，复用投票相关通用功能
 */
@Injectable()
export class ForumCommentService extends BaseCommentService {
  constructor(
    prisma: PrismaService,
    voteService: VoteService,
  ) {
    super(prisma, voteService);
  }

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
    await this.updatePostCommentCount(postId);

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
    // 使用基类的权限验证方法
    await this.validateCommentOwnership(
      commentId,
      userId,
      CommentEntityType.FORUM,
      'author_id',
    );

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
    // 使用基类的权限验证方法
    await this.validateCommentOwnership(
      commentId,
      userId,
      CommentEntityType.FORUM,
      'author_id',
    );

    const comment = await this.prisma.forumComment.findUnique({
      where: { id: commentId },
    });
    if (!comment) {
      throw new NotFoundException('Comment not found');
    }

    await this.prisma.forumComment.delete({
      where: { id: commentId },
    });

    // 更新帖子的评论计数
    await this.updatePostCommentCount(comment.post_id);
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

    // 使用基类的投票增强方法
    const commentsWithVotes = await this.enrichWithVotes(
      comments.map((c) => convertCommentToTypeOrmFormat(c)),
      CommentEntityType.FORUM,
      userId,
    );

    return commentsWithVotes;
  }

  /**
   * 更新帖子的评论计数
   * @private
   */
  private async updatePostCommentCount(postId: string): Promise<void> {
    const commentCount = await this.prisma.forumComment.count({
      where: { post_id: postId },
    });

    const post = await this.prisma.forumPost.findUnique({
      where: { id: postId },
    });

    if (post) {
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
        data: {
          stats: stats as import('@prisma/client').Prisma.InputJsonValue,
        },
      });
    }
  }
}
