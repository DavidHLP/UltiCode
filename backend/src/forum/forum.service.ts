import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { NotificationGateway } from '../notification/notification.gateway';
import {
  ForumPost,
  ForumComment,
  ForumCommunity,
  ForumCommunityRule,
  ForumCommunityLink,
  ForumTag,
  ForumCommunityMember,
} from './types';
import { ForumModerationService } from './services/forum-moderation.service';
import { ForumCommentService } from './services/forum-comment.service';
import { ForumPostService } from './services/forum-post.service';
import { ForumCommunityService } from './services/forum-community.service';

/**
 * ForumService - 主编排服务
 *
 * 职责:
 * - 协调各子服务完成复杂业务逻辑
 * - 提供 100% 向后兼容的 API
 * - 聚合查询（如 getThread 需要组合帖子和评论数据）
 */
@Injectable()
export class ForumService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly moderationService: ForumModerationService,
    private readonly commentService: ForumCommentService,
    private readonly postService: ForumPostService,
    private readonly communityService: ForumCommunityService,
    private readonly notificationGateway: NotificationGateway,
  ) {}

  // ========== 帖子操作 - 委托给 ForumPostService ==========

  async findAllPosts(userId?: string): Promise<ForumPost[]> {
    return this.postService.findAllPosts(userId);
  }

  async findOnePost(id: string, userId?: string): Promise<ForumPost | null> {
    return this.postService.findOnePost(id, userId);
  }

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
  ): Promise<ForumPost> {
    const post = await this.postService.createPost(
      input,
      author,
      this.moderationService,
      this.communityService,
    );

    // Broadcast new post notification to community subscribers
    const community = await this.communityService.findOneCommunity(
      input.communityId,
    );
    if (community.community) {
      this.notificationGateway.broadcastNewPost(input.communityId, {
        postId: post.id,
        postTitle: post.title,
        communityId: input.communityId,
        communityName: community.community.name,
        authorId: author.id,
        authorName: author.username,
        excerpt: post.excerpt || '',
      });
    }

    return post;
  }

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
  ): Promise<ForumPost> {
    return this.postService.updatePost(
      postId,
      userId,
      input,
      this.moderationService,
    );
  }

  async deletePost(postId: string, userId: string): Promise<void> {
    return this.postService.deletePost(postId, userId, this.moderationService);
  }

  async findPostsByUser(
    userId: string,
    currentUserId?: string,
  ): Promise<ForumPost[]> {
    return this.postService.findPostsByUser(userId, currentUserId);
  }

  async recordShare(postId: string): Promise<void> {
    return this.postService.recordShare(postId);
  }

  async recordView(postId: string): Promise<void> {
    return this.postService.recordView(postId);
  }

  // ========== 评论操作 - 委托给 ForumCommentService ==========

  async createComment(
    postId: string,
    body: string,
    parentId: string | null,
    author: { id: string; username: string; avatar?: string | null },
  ): Promise<ForumComment> {
    const comment = await this.commentService.createComment(
      postId,
      body,
      parentId,
      author,
      this.moderationService,
    );

    // Send notification to post author (if not the same as commenter)
    const post = await this.prisma.forumPost.findUnique({
      where: { id: postId },
      include: { community: true },
    });

    if (post && post.user_id !== author.id) {
      this.notificationGateway.sendCommentNotification(post.user_id, {
        commentId: comment.id,
        postId: postId,
        postTitle: post.title,
        communityId: post.community_id,
        authorId: author.id,
        authorName: author.username,
        content: body.substring(0, 100), // Truncate for notification
      });
    }

    return comment;
  }

  async updateComment(
    commentId: string,
    body: string,
    userId: string,
  ): Promise<ForumComment> {
    return this.commentService.updateComment(commentId, body, userId);
  }

  async deleteComment(commentId: string, userId: string): Promise<void> {
    return this.commentService.deleteComment(commentId, userId);
  }

  async getCommentCounts(postIds: string[]): Promise<Map<string, number>> {
    return this.commentService.getCommentCounts(postIds);
  }

  // ========== 社区操作 - 委托给 ForumCommunityService ==========

  async findAllCommunities(options?: {
    includePrivate?: boolean;
    featuredOnly?: boolean;
  }): Promise<ForumCommunity[]> {
    return this.communityService.findAllCommunities(options);
  }

  async findOneCommunity(slugOrId: string): Promise<{
    community: ForumCommunity | null;
    rules: ForumCommunityRule[];
    links: ForumCommunityLink[];
  }> {
    return this.communityService.findOneCommunity(slugOrId);
  }

  async findPostsByCommunity(
    communitySlug: string,
    _options?: { sortBy?: 'hot' | 'new' | 'top'; userId?: string },
  ): Promise<ForumPost[]> {
    // ForumCommunityService 返回的格式与 ForumPost 接口兼容
    const posts = await this.communityService.findPostsByCommunity(
      communitySlug,
      _options?.userId,
    );
    return posts as ForumPost[];
  }

  async createCommunity(
    userId: string,
    data: {
      name: string;
      slug: string;
      description?: string;
      icon?: string;
      color?: string;
      banner?: string;
      visibility?: 'PUBLIC' | 'PRIVATE';
    },
  ): Promise<ForumCommunity> {
    return this.communityService.createCommunity(userId, data);
  }

  async updateCommunity(
    id: string,
    userId: string,
    data: {
      name?: string;
      description?: string;
      icon?: string;
      color?: string;
      banner?: string;
      visibility?: 'PUBLIC' | 'PRIVATE';
    },
  ): Promise<ForumCommunity> {
    return this.communityService.updateCommunity(
      id,
      userId,
      data,
      this.moderationService,
    );
  }

  async deleteCommunity(id: string, userId: string): Promise<void> {
    return this.communityService.deleteCommunity(
      id,
      userId,
      this.moderationService,
    );
  }

  async joinCommunity(
    userId: string,
    communityId: string,
  ): Promise<ForumCommunityMember> {
    return this.communityService.joinCommunity(userId, communityId);
  }

  async leaveCommunity(userId: string, communityId: string): Promise<void> {
    return this.communityService.leaveCommunity(userId, communityId);
  }

  async checkMembership(userId: string, communityId: string): Promise<boolean> {
    return this.communityService.checkMembership(userId, communityId);
  }

  async findAllTags(): Promise<ForumTag[]> {
    return this.communityService.findAllTags();
  }

  // ========== 聚合查询 - 组合多个子服务的数据 ==========

  /**
   * 获取帖子及其评论树
   * 这是一个聚合查询，需要组合帖子和评论数据
   */
  async getThread(
    id: string,
    userId?: string,
  ): Promise<(ForumPost & { comments: ForumComment[] }) | null> {
    // 获取帖子数据
    const post = await this.postService.findOnePost(id, userId);

    if (!post) {
      return null;
    }

    // 获取评论树
    const comments = await this.commentService.getThread(id, userId);

    return {
      ...post,
      comments,
    } as ForumPost & { comments: ForumComment[] };
  }

  // ========== 向后兼容的辅助方法 ==========

  /**
   * 确保用户存在 - 委托给 ForumModerationService
   */
  private async ensureForumUser(user: {
    id: string;
    username: string;
    avatar?: string | null;
  }) {
    return this.moderationService.ensureForumUser(user);
  }

  /**
   * 解析 flair - 委托给 ForumModerationService
   */
  private resolveFlair(post: ForumPost) {
    return this.moderationService.resolveFlair(post);
  }

  /**
   * 规范化统计数据 - 委托给 ForumModerationService
   */
  private normalizeStats(
    post: ForumPost,
    options?: { commentsCount?: number; savesCount?: number },
  ) {
    return this.moderationService.normalizeStats(post, options);
  }

  /**
   * 规范化帖子 - 委托给 ForumModerationService
   */
  private normalizePost(
    post: ForumPost,
    options?: {
      commentsCount?: number;
      savesCount?: number;
      votes?: { likes: number; dislikes: number };
      userVote?: number;
      isSaved?: boolean;
    },
  ) {
    return this.moderationService.normalizePost(post, options);
  }

  /**
   * 检查权限 - 委托给 ForumModerationService
   */
  private async canModerateCommunity(userId: string, communityId: string) {
    return this.moderationService.canModerateCommunity(userId, communityId);
  }
}

// 重新导出类型，保持向后兼容
export type {
  ForumPost,
  ForumComment,
  ForumCommunity,
  ForumTag,
  ForumCommunityRule,
  ForumCommunityLink,
  ForumCommunityMember,
} from './types';
