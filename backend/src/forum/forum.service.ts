import {
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { randomUUID } from 'crypto';
import { PrismaService } from '../prisma.service';
import {
  ForumPost as PrismaForumPost,
  ForumComment as PrismaForumComment,
  ForumCommunity as PrismaForumCommunity,
  ForumTag as PrismaForumTag,
  ForumCommunityRule as PrismaForumCommunityRule,
  ForumCommunityLink as PrismaForumCommunityLink,
  ForumCommunityMember as PrismaForumCommunityMember,
  ForumUser as PrismaForumUser,
  FlairType,
  Prisma,
} from '@prisma/client';
import { VoteService } from '../vote/vote.service';
import { BookmarkType, EdgeOperationTargetType } from '@prisma/client';
import { BookmarkService } from '../bookmark/bookmark.service';

// Type-compatible interfaces that match TypeORM entity shape
export interface ForumPost extends Omit<PrismaForumPost, 'stats'> {
  communityId: string;
  userId: string;
  flairType: string | null;
  flairLabel: string | null;
  isPinned: boolean;
  isLocked: boolean;
  createdAt: Date;
  community: ForumCommunity;
  author: ForumUser;
  tags: string[];
  voteState?: string;
  isSaved?: boolean;
  likes?: number;
  dislikes?: number;
  score?: number;
  userVote?: 0 | 1 | -1;
  flair?: { type: string; text: string };
  stats?: { views?: number; comments?: number; saves?: number };
}

export interface ForumComment extends PrismaForumComment {
  postId: string;
  parentId: string | null;
  authorId: string;
  editedAt: Date | null;
  isPinned: boolean;
  isLocked: boolean;
  createdAt: Date;
  author: ForumUser;
  parent?: ForumComment | null;
  likes?: number;
  dislikes?: number;
  upvotes?: number;
  userVote?: number;
}

export interface ForumCommunity extends PrismaForumCommunity {
  postsCount: number;
  postsToday: number;
  postsWeek: number;
  isOfficial: boolean;
  isFeatured: boolean;
  sortOrder: number;
  createdAt: Date;
}

export interface ForumTag extends PrismaForumTag {
  usageCount: number;
  createdAt: Date;
}

export interface ForumCommunityRule extends PrismaForumCommunityRule {
  communityId: string;
  sortOrder: number;
  createdAt: Date;
}

export interface ForumCommunityLink extends PrismaForumCommunityLink {
  communityId: string;
  sortOrder: number;
}

export interface ForumCommunityMember extends PrismaForumCommunityMember {
  communityId: string;
  userId: string;
  joinedAt: Date;
}

export type ForumUser = PrismaForumUser;

// Helper function to convert Prisma result to TypeORM-compatible format
function convertPostToTypeOrmFormat(
  post: PrismaForumPost & {
    author?: PrismaForumUser | null;
    community?: PrismaForumCommunity | null;
  },
): ForumPost {
  return {
    ...post,
    communityId: post.community_id,
    userId: post.user_id,
    flairType: post.flair_type,
    flairLabel: post.flair_label,
    isPinned: post.is_pinned,
    isLocked: post.is_locked,
    createdAt: post.created_at,
    tags: post.tags as string[],
    community: post.community
      ? convertCommunityToTypeOrmFormat(post.community)
      : (undefined as never),
    author: (post.author as ForumUser) || (undefined as never),
  } as ForumPost;
}

function convertCommunityToTypeOrmFormat(
  community: PrismaForumCommunity,
): ForumCommunity {
  return {
    ...community,
    postsCount: community.posts_count,
    postsToday: community.posts_today,
    postsWeek: community.posts_week,
    isOfficial: community.is_official,
    isFeatured: community.is_featured,
    sortOrder: community.sort_order,
    createdAt: community.created_at,
  };
}

function convertCommentToTypeOrmFormat(
  comment: PrismaForumComment & {
    author?: PrismaForumUser | null;
  },
): ForumComment {
  return {
    ...comment,
    postId: comment.post_id,
    parentId: comment.parent_id,
    authorId: comment.author_id,
    editedAt: comment.edited_at,
    isPinned: comment.is_pinned,
    isLocked: comment.is_locked,
    createdAt: comment.created_at,
    author: (comment.author as ForumUser) || (undefined as never),
  } as ForumComment;
}

function convertTagToTypeOrmFormat(tag: PrismaForumTag): ForumTag {
  return {
    ...tag,
    usageCount: tag.usage_count,
    createdAt: tag.created_at,
  };
}

function convertCommunityRuleToTypeOrmFormat(
  rule: PrismaForumCommunityRule,
): ForumCommunityRule {
  return {
    ...rule,
    communityId: rule.community_id,
    sortOrder: rule.sort_order,
    createdAt: rule.created_at,
  };
}

function convertCommunityLinkToTypeOrmFormat(
  link: PrismaForumCommunityLink,
): ForumCommunityLink {
  return {
    ...link,
    communityId: link.community_id,
    sortOrder: link.sort_order,
  };
}

function convertCommunityMemberToTypeOrmFormat(
  member: PrismaForumCommunityMember,
): ForumCommunityMember {
  return {
    ...member,
    communityId: member.community_id,
    userId: member.user_id,
    joinedAt: member.joined_at,
  };
}

@Injectable()
export class ForumService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly voteService: VoteService,
    private readonly bookmarkService: BookmarkService,
  ) {}

  private async ensureForumUser(user: {
    id: string;
    username: string;
    avatar?: string | null;
  }) {
    const existing = await this.prisma.forumUser.findUnique({
      where: { id: user.id },
    });
    if (existing) return existing;

    return this.prisma.forumUser.create({
      data: {
        id: user.id,
        username: user.username,
        avatar: user.avatar ?? null,
      },
    });
  }

  private resolveFlair(post: ForumPost) {
    if (!post.flairType) return undefined;
    const text =
      post.flairLabel ||
      post.flairType.charAt(0).toUpperCase() + post.flairType.slice(1);
    return { type: post.flairType, text };
  }

  private normalizeStats(
    post: ForumPost,
    options?: { commentsCount?: number; savesCount?: number },
  ) {
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

  private async getCommentCounts(postIds: string[]) {
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

  private async canModerateCommunity(userId: string, communityId: string) {
    const membership = await this.prisma.forumCommunityMember.findUnique({
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
    const commentCounts = await this.getCommentCounts(postIds);
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
      return this.normalizePost(converted, {
        commentsCount: commentCounts.get(post.id) ?? 0,
        savesCount: favoriteCounts.get(post.id) ?? 0,
        votes: voteMap.get(post.id) || { likes: 0, dislikes: 0 },
        isSaved: bookmarkMap.get(post.id) ?? false,
      });
    });
  }

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
      data: { stats: stats as Prisma.InputJsonValue },
    });
  }

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
        stats: stats as Prisma.InputJsonValue,
      },
    });
  }

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
    const commentCounts = await this.getCommentCounts([id]);
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
    return this.normalizePost(converted, {
      commentsCount: commentCounts.get(id) ?? 0,
      savesCount: favoriteCount,
      votes: stats,
      isSaved,
    });
  }

  async getThread(
    id: string,
    userId?: string,
  ): Promise<(ForumPost & { comments: ForumComment[] }) | null> {
    const post = await this.prisma.forumPost.findUnique({
      where: { id },
      include: {
        author: true,
        community: true,
      },
    });

    if (post) {
      const comments = await this.prisma.forumComment.findMany({
        where: { post_id: id },
        include: { author: true },
        orderBy: { created_at: 'asc' },
      });

      // 1. Fetch Post Stats
      const postStats = await this.voteService.getVoteCounts(
        EdgeOperationTargetType.FORUM_POST,
        id,
      );

      // 2. Fetch Post User Vote
      let postUserVote = 0;
      if (userId) {
        const votes = await this.voteService.getUserVotesBatch(
          userId,
          EdgeOperationTargetType.FORUM_POST,
          [id],
        );
        postUserVote = votes.get(id) || 0;
      }

      // 2.5 Fetch Save Count
      const favoriteCount = await this.bookmarkService.getFavoriteCount(
        BookmarkType.FORUM_POST,
        id,
      );

      // 3. Fetch Comment Stats (Batch)
      const commentIds = comments.map((c) => c.id);
      const commentVoteMap = await this.voteService.getVoteCountsBatch(
        EdgeOperationTargetType.FORUM_COMMENT,
        commentIds,
      );

      // 4. Fetch Comment User Votes (Batch)
      let commentUserVoteMap = new Map<string, number>();
      if (userId) {
        commentUserVoteMap = await this.voteService.getUserVotesBatch(
          userId,
          EdgeOperationTargetType.FORUM_COMMENT,
          commentIds,
        );
      }

      // 5. Check Bookmark Status
      let isSaved = false;
      if (userId) {
        isSaved = await this.bookmarkService.isInDefaultFolder(
          userId,
          BookmarkType.FORUM_POST,
          id,
        );
      }

      // 6. Map everything to comments
      const uniqueComments = comments.map((comment) => {
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

      const convertedPost = convertPostToTypeOrmFormat(post);
      const normalizedPost = this.normalizePost(convertedPost, {
        commentsCount: comments.length,
        savesCount: favoriteCount,
        votes: postStats,
        userVote: postUserVote,
        isSaved,
      });

      return {
        ...normalizedPost,
        comments: uniqueComments as ForumComment[],
      } as ForumPost & { comments: ForumComment[] };
    }

    // Fallback to seed data if database is empty
    return null;
  }

  // Enhanced community fetching with filtering
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

  // Find community by slug or ID with rules and links
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

  // Get posts by community slug
  async findPostsByCommunity(
    communitySlug: string,
    _options?: { sortBy?: 'hot' | 'new' | 'top'; userId?: string },
  ): Promise<ForumPost[]> {
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
    const commentCounts = await this.getCommentCounts(postIds);
    const favoriteCounts = await this.bookmarkService.getFavoriteCountsBatch(
      BookmarkType.FORUM_POST,
      postIds,
    );

    let bookmarkMap = new Map<string, boolean>();
    if (_options?.userId) {
      bookmarkMap = await this.bookmarkService.getBookmarkStatusBatch(
        _options.userId,
        BookmarkType.FORUM_POST,
        postIds,
      );
    }

    return posts.map((post) => {
      const converted = convertPostToTypeOrmFormat(post);
      return this.normalizePost(converted, {
        commentsCount: commentCounts.get(post.id) ?? 0,
        savesCount: favoriteCounts.get(post.id) ?? 0,
        votes: voteMap.get(post.id) || { likes: 0, dislikes: 0 },
        isSaved: bookmarkMap.get(post.id) ?? false,
      });
    });
  }

  // Tag management
  async findAllTags(): Promise<ForumTag[]> {
    const tags = await this.prisma.forumTag.findMany({
      orderBy: { usage_count: 'desc' },
    });
    return tags.map((t) => convertTagToTypeOrmFormat(t));
  }

  // Membership operations
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

  async checkMembership(userId: string, communityId: string): Promise<boolean> {
    const count = await this.prisma.forumCommunityMember.count({
      where: {
        community_id: communityId,
        user_id: userId,
      },
    });
    return count > 0;
  }

  async createComment(
    postId: string,
    body: string,
    parentId: string | null,
    author: { id: string; username: string; avatar?: string | null },
  ): Promise<ForumComment> {
    const post = await this.prisma.forumPost.findUnique({
      where: { id: postId },
    });
    if (!post) {
      throw new NotFoundException('Post not found');
    }

    await this.ensureForumUser(author);

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
    const community = await this.prisma.forumCommunity.findUnique({
      where: { id: input.communityId },
    });
    if (!community) {
      throw new NotFoundException('Community not found');
    }
    if (community.visibility !== 'PUBLIC') {
      const isMember = await this.checkMembership(author.id, community.id);
      if (!isMember) {
        throw new ForbiddenException('Community is restricted');
      }
    }

    await this.ensureForumUser(author);

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
        media: input.media as Prisma.InputJsonValue,
        created_at: new Date(),
        stats: { comments: 0, views: 0 } as Prisma.InputJsonValue,
      },
      include: { author: true, community: true },
    });

    await this.prisma.forumCommunity.update({
      where: { id: input.communityId },
      data: { posts_count: { increment: 1 } },
    });

    const converted = convertPostToTypeOrmFormat(post);
    return this.normalizePost(converted, {
      commentsCount: 0,
      votes: { likes: 0, dislikes: 0 },
    });
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
    const post = await this.prisma.forumPost.findUnique({
      where: { id: postId },
      include: { community: true },
    });
    if (!post) {
      throw new NotFoundException('Post not found');
    }

    const isAuthor = post.user_id === userId;
    const canModerate = await this.canModerateCommunity(
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

    const updateData: Prisma.ForumPostUpdateInput = {};
    if (isAuthor) {
      if (input.title !== undefined) updateData.title = input.title;
      if (input.excerpt !== undefined) updateData.excerpt = input.excerpt;
      if (input.tags !== undefined) updateData.tags = input.tags;
      if (input.flairType !== undefined)
        updateData.flair_type = input.flairType as FlairType;
      if (input.flairLabel !== undefined)
        updateData.flair_label = input.flairLabel;
      if (input.media !== undefined)
        updateData.media = input.media as Prisma.InputJsonValue;
    }
    if (input.isPinned !== undefined) updateData.is_pinned = input.isPinned;
    if (input.isLocked !== undefined) updateData.is_locked = input.isLocked;

    const saved = await this.prisma.forumPost.update({
      where: { id: postId },
      data: updateData,
      include: { author: true, community: true },
    });

    const stats = (saved.stats as { comments?: number }) || {};
    const converted = convertPostToTypeOrmFormat(saved);
    return this.normalizePost(converted, {
      commentsCount: stats.comments ?? 0,
      votes: { likes: 0, dislikes: 0 },
    });
  }

  async deletePost(postId: string, userId: string): Promise<void> {
    const post = await this.prisma.forumPost.findUnique({
      where: { id: postId },
    });
    if (!post) {
      throw new NotFoundException('Post not found');
    }

    const isAuthor = post.user_id === userId;
    const canModerate = await this.canModerateCommunity(
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
    const commentCounts = await this.getCommentCounts(postIds);
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
      return this.normalizePost(converted, {
        commentsCount: commentCounts.get(post.id) ?? 0,
        savesCount: favoriteCounts.get(post.id) ?? 0,
        votes: voteMap.get(post.id) || { likes: 0, dislikes: 0 },
        isSaved: bookmarkMap.get(post.id) ?? false,
      });
    });
  }
}
