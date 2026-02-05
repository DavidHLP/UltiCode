import { Injectable } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { PrismaService } from '../../prisma.service';
import { VoteService } from '../../vote/vote.service';
import { BookmarkService } from '../../bookmark/bookmark.service';
import { BookmarkType, EdgeOperationTargetType } from '@prisma/client';
import type {
  ForumCommunity,
  ForumCommunityRule,
  ForumCommunityLink,
  ForumCommunityMember,
  ForumTag,
  PrismaClient,
} from '../types';
import {
  convertCommunityToTypeOrmFormat,
  convertCommunityRuleToTypeOrmFormat,
  convertCommunityLinkToTypeOrmFormat,
  convertCommunityMemberToTypeOrmFormat,
  convertTagToTypeOrmFormat,
} from '../types';

/**
 * ForumCommunityService - 社区和成员管理
 *
 * 职责:
 * - 社区 CRUD 操作
 * - 社区成员管理（加入、离开、检查）
 * - 按社区查询帖子
 * - 获取标签列表
 */
@Injectable()
export class ForumCommunityService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly voteService: VoteService,
    private readonly bookmarkService: BookmarkService,
  ) {}

  /**
   * 获取所有社区
   */
  async findAllCommunities(options?: {
    includePrivate?: boolean;
    featuredOnly?: boolean;
  }): Promise<ForumCommunity[]> {
    const where: Record<string, unknown> = {};

    if (!options?.includePrivate) {
      where.visibility = { not: 'PRIVATE' };
    }

    if (options?.featuredOnly) {
      where.is_featured = true;
    }

    const communities = await this.prisma.forumCommunity.findMany({
      where: where as never,
      orderBy: [{ sort_order: 'asc' }, { created_at: 'desc' }],
    });

    return communities.map((c) => convertCommunityToTypeOrmFormat(c));
  }

  /**
   * 根据 slug 或 ID 获取单个社区（包含规则和链接）
   */
  async findOneCommunity(slugOrId: string): Promise<{
    community: ForumCommunity | null;
    rules: ForumCommunityRule[];
    links: ForumCommunityLink[];
  }> {
    const community = await this.prisma.forumCommunity.findFirst({
      where: { OR: [{ id: slugOrId }, { slug: slugOrId }] },
    });

    if (!community) {
      return { community: null, rules: [], links: [] };
    }

    const [rules, links] = await Promise.all([
      this.prisma.forumCommunityRule.findMany({
        where: { community_id: community.id },
        orderBy: { sort_order: 'asc' },
      }),
      this.prisma.forumCommunityLink.findMany({
        where: { community_id: community.id },
        orderBy: { sort_order: 'asc' },
      }),
    ]);

    return {
      community: convertCommunityToTypeOrmFormat(community),
      rules: rules.map((r) => convertCommunityRuleToTypeOrmFormat(r)),
      links: links.map((l) => convertCommunityLinkToTypeOrmFormat(l)),
    };
  }

  /**
   * 获取社区的帖子
   */
  async findPostsByCommunity(
    communitySlug: string,
    userId?: string,
  ): Promise<
    {
      id: string;
      title: string;
      excerpt: string | null;
      community_id: string;
      user_id: string;
      created_at: Date;
      author: {
        id: string;
        username: string;
        avatar: string | null;
        karma: number;
      };
      community: ForumCommunity;
      likes: number;
      dislikes: number;
      score: number;
      stats: { views: number; comments: number; saves: number };
      isSaved: boolean;
    }[]
  > {
    const community = await this.prisma.forumCommunity.findUnique({
      where: { slug: communitySlug },
    });

    if (!community) {
      return [];
    }

    const posts = await this.prisma.forumPost.findMany({
      where: { community_id: community.id },
      include: { author: true, community: true },
      orderBy: { created_at: 'desc' },
    });

    // Get vote counts for all posts
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
      return {
        id: post.id,
        title: post.title,
        excerpt: post.excerpt,
        community_id: post.community_id,
        user_id: post.user_id,
        created_at: post.created_at,
        author: post.author,
        community: convertCommunityToTypeOrmFormat(post.community),
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
      };
    });
  }

  /**
   * 创建社区
   */
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
    const createData: Record<string, unknown> = {
      name: data.name,
      slug: data.slug,
      visibility: data.visibility ?? 'PUBLIC',
      members: 1,
    };
    if (data.description !== undefined)
      createData.description = data.description;
    if (data.icon !== undefined) createData.icon = data.icon;
    if (data.color !== undefined) createData.color = data.color;
    if (data.banner !== undefined) createData.banner = data.banner;

    const community = await this.prisma.forumCommunity.create({
      data: createData as never,
    });

    // 创建者自动成为所有者
    await this.prisma.forumCommunityMember.create({
      data: {
        id: randomUUID(),
        user_id: userId,
        community_id: community.id,
        role: 'OWNER',
        joined_at: new Date(),
      },
    });

    return convertCommunityToTypeOrmFormat(community);
  }

  /**
   * 更新社区
   */
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
    moderationService: {
      canModerateCommunity: (
        userId: string,
        communityId: string,
        tx?: PrismaClient,
      ) => Promise<boolean>;
    },
  ): Promise<ForumCommunity> {
    const canModerate = await moderationService.canModerateCommunity(
      userId,
      id,
    );

    if (!canModerate) {
      throw new Error('Not allowed to update this community');
    }

    const updated = await this.prisma.forumCommunity.update({
      where: { id },
      data: {
        name: data.name,
        description: data.description,
        icon: data.icon,
        color: data.color,
        banner: data.banner,
        visibility: data.visibility,
      },
    });

    return convertCommunityToTypeOrmFormat(updated);
  }

  /**
   * 删除社区
   */
  async deleteCommunity(
    id: string,
    userId: string,
    moderationService: {
      canModerateCommunity: (
        userId: string,
        communityId: string,
        tx?: PrismaClient,
      ) => Promise<boolean>;
    },
  ): Promise<void> {
    const canModerate = await moderationService.canModerateCommunity(
      userId,
      id,
    );

    if (!canModerate) {
      throw new Error('Not allowed to delete this community');
    }

    await this.prisma.forumCommunity.delete({
      where: { id },
    });
  }

  /**
   * 加入社区
   */
  async joinCommunity(
    userId: string,
    communityId: string,
  ): Promise<ForumCommunityMember> {
    // Check if already a member
    const existing = await this.prisma.forumCommunityMember.findUnique({
      where: {
        community_id_user_id: {
          community_id: communityId,
          user_id: userId,
        },
      },
    });

    if (existing) {
      return convertCommunityMemberToTypeOrmFormat(existing);
    }

    const member = await this.prisma.forumCommunityMember.create({
      data: {
        id: randomUUID(),
        user_id: userId,
        community_id: communityId,
        role: 'MEMBER',
        joined_at: new Date(),
      },
    });

    // Increment member count
    await this.prisma.forumCommunity.update({
      where: { id: communityId },
      data: { members: { increment: 1 } },
    });

    return convertCommunityMemberToTypeOrmFormat(member);
  }

  /**
   * 离开社区
   */
  async leaveCommunity(userId: string, communityId: string): Promise<void> {
    const member = await this.prisma.forumCommunityMember.findUnique({
      where: {
        community_id_user_id: {
          community_id: communityId,
          user_id: userId,
        },
      },
    });

    if (member) {
      await this.prisma.forumCommunityMember.delete({
        where: {
          community_id_user_id: {
            community_id: communityId,
            user_id: userId,
          },
        },
      });
      await this.prisma.forumCommunity.update({
        where: { id: communityId },
        data: { members: { decrement: 1 } },
      });
    }
  }

  /**
   * 检查用户是否是社区成员
   */
  async checkMembership(userId: string, communityId: string): Promise<boolean> {
    const count = await this.prisma.forumCommunityMember.count({
      where: {
        community_id: communityId,
        user_id: userId,
      },
    });
    return count > 0;
  }

  /**
   * 获取所有标签
   */
  async findAllTags(): Promise<ForumTag[]> {
    const tags = await this.prisma.forumTag.findMany({
      orderBy: { usage_count: 'desc' },
    });
    return tags.map((t) => convertTagToTypeOrmFormat(t));
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
