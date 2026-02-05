import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { ForumUser } from '@prisma/client';
import type { ForumPost, PrismaClient } from '../types';

/**
 * ForumModerationService - 审核和权限管理
 *
 * 职责:
 * - 检查用户权限（版主/所有者）
 * - 确保 ForumUser 存在
 * - 数据格式化辅助方法
 */
@Injectable()
export class ForumModerationService {
  constructor(private readonly prisma: PrismaService) {}

  /**
   * 确保用户在 ForumUser 表中存在
   * 如果不存在则创建
   */
  async ensureForumUser(
    user: { id: string; username: string; avatar?: string | null },
    tx?: PrismaClient,
  ): Promise<ForumUser> {
    const prisma = tx ?? this.prisma;

    const existing = await prisma.forumUser.findUnique({
      where: { id: user.id },
    });
    if (existing) return existing;

    return prisma.forumUser.create({
      data: {
        id: user.id,
        username: user.username,
        avatar: user.avatar ?? null,
      },
    });
  }

  /**
   * 检查用户是否可以管理社区（所有者或版主）
   */
  async canModerateCommunity(
    userId: string,
    communityId: string,
    tx?: PrismaClient,
  ): Promise<boolean> {
    const prisma = tx ?? this.prisma;

    const membership = await prisma.forumCommunityMember.findUnique({
      where: {
        community_id_user_id: {
          community_id: communityId,
          user_id: userId,
        },
      },
    });
    if (!membership) return false;
    return membership.role === 'OWNER' || membership.role === 'MODERATOR';
  }

  /**
   * 解析帖子的 flair 信息
   */
  resolveFlair(post: ForumPost): { type: string; text: string } | undefined {
    if (!post.flairType) return undefined;
    const text =
      post.flairLabel ||
      post.flairType.charAt(0).toUpperCase() + post.flairType.slice(1);
    return { type: post.flairType, text };
  }

  /**
   * 规范化帖子统计数据
   */
  normalizeStats(
    post: ForumPost,
    options?: { commentsCount?: number; savesCount?: number },
  ): { views: number; comments: number; saves: number } {
    const stats = post.stats as {
      views?: number;
      comments?: number;
      saves?: number;
    } | null;
    return {
      ...(stats || {}),
      comments: options?.commentsCount ?? stats?.comments ?? 0,
      views: post.views ?? stats?.views ?? 0,
      saves: options?.savesCount ?? stats?.saves ?? 0,
    };
  }

  /**
   * 规范化帖子数据，添加 flair、stats、投票信息
   */
  normalizePost(
    post: ForumPost,
    options?: {
      commentsCount?: number;
      savesCount?: number;
      votes?: { likes: number; dislikes: number };
      userVote?: number;
      isSaved?: boolean;
    },
  ): ForumPost {
    const flair = this.resolveFlair(post);
    const stats = this.normalizeStats(post, {
      commentsCount: options?.commentsCount,
      savesCount: options?.savesCount,
    });
    const voteState =
      options?.userVote === 1
        ? 'upvoted'
        : options?.userVote === -1
          ? 'downvoted'
          : 'neutral';

    return {
      ...post,
      flair,
      stats,
      likes: options?.votes?.likes ?? 0,
      dislikes: options?.votes?.dislikes ?? 0,
      score:
        options?.votes?.likes !== undefined &&
        options?.votes?.dislikes !== undefined
          ? options.votes.likes - options.votes.dislikes
          : 0,
      userVote: (options?.userVote ?? 0) as 0 | 1 | -1,
      voteState,
      isSaved: options?.isSaved ?? false,
    } as ForumPost;
  }

  /**
   * 置顶帖子
   */
  async pinPost(postId: string, moderatorId: string): Promise<void> {
    const post = await this.prisma.forumPost.findUnique({
      where: { id: postId },
      include: { community: true },
    });

    if (!post) {
      throw new NotFoundException('Post not found');
    }

    const canModerate = await this.canModerateCommunity(
      moderatorId,
      post.community_id,
    );

    if (!canModerate) {
      throw new NotFoundException('Not allowed to pin this post');
    }

    await this.prisma.forumPost.update({
      where: { id: postId },
      data: { is_pinned: true },
    });
  }

  /**
   * 锁定帖子
   */
  async lockPost(postId: string, moderatorId: string): Promise<void> {
    const post = await this.prisma.forumPost.findUnique({
      where: { id: postId },
      include: { community: true },
    });

    if (!post) {
      throw new NotFoundException('Post not found');
    }

    const canModerate = await this.canModerateCommunity(
      moderatorId,
      post.community_id,
    );

    if (!canModerate) {
      throw new NotFoundException('Not allowed to lock this post');
    }

    await this.prisma.forumPost.update({
      where: { id: postId },
      data: { is_locked: true },
    });
  }
}
