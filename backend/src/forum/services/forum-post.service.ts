import {
  Injectable,
  ForbiddenException,
  NotFoundException,
} from '@nestjs/common';
import { randomUUID } from 'crypto';
import { PrismaService } from '../../prisma.service';
import { VoteService } from '../../vote/vote.service';
import { BookmarkService } from '../../bookmark/bookmark.service';
import {
  BookmarkType,
  EdgeOperationTargetType,
  FlairType,
} from '@prisma/client';
import type { ForumPost, PrismaClient } from '../types';
import { convertPostToTypeOrmFormat } from '../types';
import type { ForumCommunityService } from './forum-community.service';

/**
 * ForumPostService - 帖子 CRUD 和查询
 *
 * 职责:
 * - 创建、更新、删除帖子
 * - 查询帖子列表（全部、按用户）
 * - 获取单个帖子详情
 * - 记录分享和浏览
 */
@Injectable()
export class ForumPostService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly voteService: VoteService,
    private readonly bookmarkService: BookmarkService,
  ) {}

  /**
   * 获取所有帖子（带分页和统计）
   */
  async findAllPosts(userId?: string): Promise<ForumPost[]> {
    const posts = await this.prisma.forumPost.findMany({
      include: {
        author: true,
        community: true,
      },
      orderBy: { created_at: 'desc' },
    });

    const postIds = posts.map((p) => p.id);
    const voteMap = await this.voteService.getVoteCountsBatch(
      EdgeOperationTargetType.FORUM_POST,
      postIds,
    );
    const commentCounts = await this.getCommentCountsBatch(postIds);
    const favoriteCounts = await this.bookmarkService.getFavoriteCountsBatch(
      BookmarkType.FORUM_POST,
      postIds,
    );

    let bookmarkMap = new Map<string, boolean>();
    if (userId) {
      bookmarkMap = await this.bookmarkService.getBookmarkStatusBatch(
        userId,
        BookmarkType.FORUM_POST,
        postIds,
      );
    }

    return posts.map((post) => {
      const converted = convertPostToTypeOrmFormat(post);
      return {
        ...converted,
        likes: voteMap.get(post.id)?.likes ?? 0,
        dislikes: voteMap.get(post.id)?.dislikes ?? 0,
        score:
          (voteMap.get(post.id)?.likes ?? 0) -
          (voteMap.get(post.id)?.dislikes ?? 0),
        stats: {
          views: post.views ?? 0,
          comments: commentCounts.get(post.id) ?? 0,
          saves: favoriteCounts.get(post.id) ?? 0,
        },
        isSaved: bookmarkMap.get(post.id) ?? false,
      } as ForumPost;
    });
  }

  /**
   * 获取单个帖子详情
   */
  async findOnePost(id: string, userId?: string): Promise<ForumPost | null> {
    const post = await this.prisma.forumPost.findUnique({
      where: { id },
      include: {
        author: true,
        community: true,
      },
    });

    if (!post) {
      return null;
    }

    const stats: { likes: number; dislikes: number } =
      await this.voteService.getVoteCounts(
        EdgeOperationTargetType.FORUM_POST,
        id,
      );
    const commentCounts = await this.getCommentCountsBatch([id]);
    const favoriteCount = await this.bookmarkService.getFavoriteCount(
      BookmarkType.FORUM_POST,
      id,
    );

    let isSaved = false;
    if (userId) {
      isSaved = await this.bookmarkService.isInDefaultFolder(
        userId,
        BookmarkType.FORUM_POST,
        id,
      );
    }

    const converted = convertPostToTypeOrmFormat(post);
    return {
      ...converted,
      likes: stats.likes,
      dislikes: stats.dislikes,
      score: stats.likes - stats.dislikes,
      stats: {
        views: post.views ?? 0,
        comments: commentCounts.get(id) ?? 0,
        saves: favoriteCount,
      },
      isSaved,
    } as ForumPost;
  }

  /**
   * 创建帖子
   */
  async createPost(
    input: {
      title: string;
      excerpt?: string | null;
      communityId: string;
      tags?: string[];
      flairType?: string | null;
      flairLabel?: string | null;
      media?: Record<string, unknown>[] | null;
    },
    author: { id: string; username: string; avatar?: string | null },
    moderationService: {
      ensureForumUser: (
        user: { id: string; username: string; avatar?: string | null },
        tx?: PrismaClient,
      ) => Promise<unknown>;
      normalizePost: (
        post: ForumPost,
        options?: {
          commentsCount?: number;
          savesCount?: number;
          votes?: { likes: number; dislikes: number };
          userVote?: number;
          isSaved?: boolean;
        },
      ) => ForumPost;
    },
    communityService: ForumCommunityService,
  ): Promise<ForumPost> {
    const community = await this.prisma.forumCommunity.findUnique({
      where: { id: input.communityId },
    });
    if (!community) {
      throw new NotFoundException('Community not found');
    }
    if (community.visibility !== 'PUBLIC') {
      const isMember = await communityService.checkMembership(
        author.id,
        community.id,
      );
      if (!isMember) {
        throw new ForbiddenException('Community is restricted');
      }
    }

    await moderationService.ensureForumUser(author);

    const post = await this.prisma.forumPost.create({
      data: {
        id: randomUUID(),
        community_id: input.communityId,
        user_id: author.id,
        title: input.title,
        excerpt: input.excerpt ?? null,
        tags: input.tags ?? [],
        flair_type: (input.flairType as FlairType | null) ?? null,
        flair_label: input.flairLabel ?? null,
        media: input.media as never,
        created_at: new Date(),
        stats: { comments: 0, views: 0 } as never,
      },
      include: { author: true, community: true },
    });

    await this.prisma.forumCommunity.update({
      where: { id: input.communityId },
      data: { posts_count: { increment: 1 } },
    });

    const converted = convertPostToTypeOrmFormat(post);
    return moderationService.normalizePost(converted, {
      commentsCount: 0,
      votes: { likes: 0, dislikes: 0 },
    });
  }

  /**
   * 更新帖子
   */
  async updatePost(
    postId: string,
    userId: string,
    input: {
      title?: string;
      excerpt?: string | null;
      tags?: string[];
      flairType?: string | null;
      flairLabel?: string | null;
      media?: Record<string, unknown>[] | null;
      isPinned?: boolean;
      isLocked?: boolean;
    },
    moderationService: {
      canModerateCommunity: (
        userId: string,
        communityId: string,
        tx?: PrismaClient,
      ) => Promise<boolean>;
      normalizePost: (
        post: ForumPost,
        options?: {
          commentsCount?: number;
          savesCount?: number;
          votes?: { likes: number; dislikes: number };
          userVote?: number;
          isSaved?: boolean;
        },
      ) => ForumPost;
    },
  ): Promise<ForumPost> {
    const post = await this.prisma.forumPost.findUnique({
      where: { id: postId },
      include: { community: true },
    });
    if (!post) {
      throw new NotFoundException('Post not found');
    }

    const isAuthor = post.user_id === userId;
    const canModerate = await moderationService.canModerateCommunity(
      userId,
      post.community_id,
    );

    if (!isAuthor && !canModerate) {
      throw new ForbiddenException('Not allowed to edit this post');
    }

    const wantsModeration =
      input.isPinned !== undefined || input.isLocked !== undefined;

    if (wantsModeration && !canModerate) {
      throw new ForbiddenException('Not allowed to manage this post');
    }

    const updateData: Record<string, unknown> = {};
    if (isAuthor) {
      if (input.title !== undefined) updateData.title = input.title;
      if (input.excerpt !== undefined) updateData.excerpt = input.excerpt;
      if (input.tags !== undefined) updateData.tags = input.tags;
      if (input.flairType !== undefined)
        updateData.flair_type = input.flairType;
      if (input.flairLabel !== undefined)
        updateData.flair_label = input.flairLabel;
      if (input.media !== undefined) updateData.media = input.media;
    }
    if (input.isPinned !== undefined) updateData.is_pinned = input.isPinned;
    if (input.isLocked !== undefined) updateData.is_locked = input.isLocked;

    const saved = await this.prisma.forumPost.update({
      where: { id: postId },
      data: updateData as never,
      include: { author: true, community: true },
    });

    const stats = (saved.stats as { comments?: number }) || {};
    const converted = convertPostToTypeOrmFormat(saved);
    return moderationService.normalizePost(converted, {
      commentsCount: stats.comments ?? 0,
      votes: { likes: 0, dislikes: 0 },
    });
  }

  /**
   * 删除帖子
   */
  async deletePost(
    postId: string,
    userId: string,
    moderationService: {
      canModerateCommunity: (
        userId: string,
        communityId: string,
        tx?: PrismaClient,
      ) => Promise<boolean>;
    },
  ): Promise<void> {
    const post = await this.prisma.forumPost.findUnique({
      where: { id: postId },
    });
    if (!post) {
      throw new NotFoundException('Post not found');
    }

    const isAuthor = post.user_id === userId;
    const canModerate = await moderationService.canModerateCommunity(
      userId,
      post.community_id,
    );

    if (!isAuthor && !canModerate) {
      throw new ForbiddenException('Not allowed to delete this post');
    }

    await this.prisma.forumPost.delete({
      where: { id: postId },
    });
    await this.prisma.forumCommunity.update({
      where: { id: post.community_id },
      data: { posts_count: { decrement: 1 } },
    });
  }

  /**
   * 获取用户发布的帖子
   */
  async findPostsByUser(
    userId: string,
    currentUserId?: string,
  ): Promise<ForumPost[]> {
    const posts = await this.prisma.forumPost.findMany({
      where: { user_id: userId },
      include: { author: true, community: true },
      orderBy: { created_at: 'desc' },
    });
    const postIds = posts.map((p) => p.id);
    const voteMap = await this.voteService.getVoteCountsBatch(
      EdgeOperationTargetType.FORUM_POST,
      postIds,
    );
    const commentCounts = await this.getCommentCountsBatch(postIds);
    const favoriteCounts = await this.bookmarkService.getFavoriteCountsBatch(
      BookmarkType.FORUM_POST,
      postIds,
    );

    let bookmarkMap = new Map<string, boolean>();
    if (currentUserId) {
      bookmarkMap = await this.bookmarkService.getBookmarkStatusBatch(
        currentUserId,
        BookmarkType.FORUM_POST,
        postIds,
      );
    }

    return posts.map((post) => {
      const converted = convertPostToTypeOrmFormat(post);
      return {
        ...converted,
        likes: voteMap.get(post.id)?.likes ?? 0,
        dislikes: voteMap.get(post.id)?.dislikes ?? 0,
        score:
          (voteMap.get(post.id)?.likes ?? 0) -
          (voteMap.get(post.id)?.dislikes ?? 0),
        stats: {
          views: post.views ?? 0,
          comments: commentCounts.get(post.id) ?? 0,
          saves: favoriteCounts.get(post.id) ?? 0,
        },
        isSaved: bookmarkMap.get(post.id) ?? false,
      } as ForumPost;
    });
  }

  /**
   * 记录分享
   */
  async recordShare(postId: string): Promise<void> {
    const post = await this.prisma.forumPost.findUnique({
      where: { id: postId },
    });
    if (!post) {
      throw new NotFoundException('Post not found');
    }

    const stats =
      (post.stats as {
        shares?: number;
        views?: number;
        comments?: number;
        saves?: number;
      }) || {};
    stats.shares = (stats.shares || 0) + 1;

    await this.prisma.forumPost.update({
      where: { id: postId },
      data: { stats: stats as never },
    });
  }

  /**
   * 记录浏览
   */
  async recordView(postId: string): Promise<void> {
    const post = await this.prisma.forumPost.findUnique({
      where: { id: postId },
    });
    if (!post) {
      throw new NotFoundException('Post not found');
    }

    const stats =
      (post.stats as {
        shares?: number;
        views?: number;
        comments?: number;
        saves?: number;
      }) || {};
    stats.views = (stats.views || 0) + 1;

    await this.prisma.forumPost.update({
      where: { id: postId },
      data: {
        views: (post.views || 0) + 1,
        stats: stats as never,
      },
    });
  }

  /**
   * 批量获取评论数（内部方法）
   */
  private async getCommentCountsBatch(
    postIds: string[],
  ): Promise<Map<string, number>> {
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
}
