import {
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { randomUUID } from 'crypto';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { ForumPost } from './entities/post.entity';
import { ForumCommunity } from './entities/community.entity';
import { ForumComment } from './entities/comment.entity';
import { ForumTag } from './entities/tag.entity';
import { ForumCommunityRule } from './entities/community-rule.entity';
import { ForumCommunityLink } from './entities/community-link.entity';
import { ForumCommunityMember } from './entities/community-member.entity';
import { ForumUser } from './entities/user.entity';
import { VoteService } from '../vote/vote.service';
import { EdgeOperationTargetType } from '@prisma/client';

@Injectable()
export class ForumService {
  constructor(
    @InjectRepository(ForumPost)
    private postsRepository: Repository<ForumPost>,
    @InjectRepository(ForumCommunity)
    private communitiesRepository: Repository<ForumCommunity>,
    @InjectRepository(ForumComment)
    private commentsRepository: Repository<ForumComment>,
    @InjectRepository(ForumTag)
    private tagsRepository: Repository<ForumTag>,
    @InjectRepository(ForumCommunityRule)
    private rulesRepository: Repository<ForumCommunityRule>,
    @InjectRepository(ForumCommunityLink)
    private linksRepository: Repository<ForumCommunityLink>,
    @InjectRepository(ForumCommunityMember)
    private membersRepository: Repository<ForumCommunityMember>,
    @InjectRepository(ForumUser)
    private forumUsersRepository: Repository<ForumUser>,
    private readonly voteService: VoteService,
  ) {}

  private async ensureForumUser(user: {
    id: string;
    username: string;
    avatar?: string | null;
  }) {
    const existing = await this.forumUsersRepository.findOne({
      where: { id: user.id },
    });
    if (existing) return existing;

    const forumUser = this.forumUsersRepository.create({
      id: user.id,
      username: user.username,
      avatar: user.avatar ?? null,
    });
    return this.forumUsersRepository.save(forumUser);
  }

  private resolveFlair(post: ForumPost) {
    if (!post.flairType) return undefined;
    const text =
      post.flairLabel ||
      post.flairType.charAt(0).toUpperCase() + post.flairType.slice(1);
    return { type: post.flairType, text };
  }

  private normalizeStats(post: ForumPost, commentsCount?: number) {
    return {
      ...(post.stats ?? {}),
      comments: commentsCount ?? post.stats?.comments ?? 0,
      views: post.views ?? post.stats?.views ?? 0,
    };
  }

  private normalizePost(
    post: ForumPost,
    options?: {
      commentsCount?: number;
      votes?: { likes: number; dislikes: number };
      userVote?: number;
    },
  ) {
    const flair = this.resolveFlair(post);
    const stats = this.normalizeStats(post, options?.commentsCount);
    const voteState =
      options?.userVote === 1
        ? 'upvoted'
        : options?.userVote === -1
          ? 'downvoted'
          : 'neutral';

    return {
      ...post,
      tags: Array.isArray(post.tags) ? post.tags : [],
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
    } as unknown as ForumPost;
  }

  private async getCommentCounts(postIds: string[]) {
    if (postIds.length === 0) return new Map<string, number>();
    const rows = await this.commentsRepository
      .createQueryBuilder('comment')
      .select('comment.postId', 'postId')
      .addSelect('COUNT(comment.id)', 'count')
      .where('comment.postId IN (:...postIds)', { postIds })
      .groupBy('comment.postId')
      .getRawMany<{ postId: string; count: string }>();

    const counts = new Map<string, number>();
    rows.forEach((row) => {
      counts.set(row.postId, Number(row.count));
    });
    return counts;
  }

  private async canModerateCommunity(userId: string, communityId: string) {
    const membership = await this.membersRepository.findOne({
      where: { userId, communityId },
    });
    if (!membership) return false;
    return membership.role === 'OWNER' || membership.role === 'MODERATOR';
  }

  async findAllPosts(): Promise<ForumPost[]> {
    const posts = await this.postsRepository.find({
      relations: ['author', 'community'],
      order: { createdAt: 'DESC' },
    });

    const postIds = posts.map((p) => p.id);
    const voteMap = await this.voteService.getVoteCountsBatch(
      EdgeOperationTargetType.FORUM_POST,
      postIds,
    );
    const commentCounts = await this.getCommentCounts(postIds);

    return posts.map((post) =>
      this.normalizePost(post, {
        commentsCount: commentCounts.get(post.id) ?? 0,
        votes: voteMap.get(post.id) || { likes: 0, dislikes: 0 },
      }),
    );
  }

  async findOnePost(id: string): Promise<ForumPost | null> {
    const post = await this.postsRepository.findOne({
      where: { id },
      relations: ['author', 'community'],
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
    return this.normalizePost(post, {
      commentsCount: commentCounts.get(id) ?? 0,
      votes: stats,
    });
  }

  async getThread(
    id: string,
    userId?: string,
  ): Promise<(ForumPost & { comments: ForumComment[] }) | null> {
    const post = await this.postsRepository.findOne({
      where: { id },
      relations: ['author', 'community'],
    });

    if (post) {
      const comments = await this.commentsRepository.find({
        where: { postId: id },
        relations: ['author'],
        order: { createdAt: 'ASC' },
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

      // 5. Map everything to comments
      const uniqueComments = comments.map((comment) => {
        const stats = commentVoteMap.get(comment.id) || {
          likes: 0,
          dislikes: 0,
        };
        return {
          ...comment,
          likes: stats.likes,
          dislikes: stats.dislikes,
          upvotes: stats.likes,
          userVote: commentUserVoteMap.get(comment.id) || 0,
        };
      });

      const normalizedPost = this.normalizePost(post, {
        commentsCount: comments.length,
        votes: postStats,
        userVote: postUserVote,
      });

      return {
        ...normalizedPost,
        comments: uniqueComments as unknown as ForumComment[],
      } as unknown as ForumPost & { comments: ForumComment[] };
    }

    // Fallback to seed data if database is empty
    return null;
  }

  // Enhanced community fetching with filtering
  async findAllCommunities(options?: {
    includePrivate?: boolean;
    featuredOnly?: boolean;
  }): Promise<ForumCommunity[]> {
    const query = this.communitiesRepository.createQueryBuilder('community');

    if (!options?.includePrivate) {
      query.andWhere('community.visibility != :private', {
        private: 'PRIVATE',
      });
    }

    if (options?.featuredOnly) {
      query.andWhere('community.isFeatured = :featured', { featured: true });
    }

    query
      .orderBy('community.sortOrder', 'ASC')
      .addOrderBy('community.createdAt', 'DESC');

    return query.getMany();
  }

  // Find community by slug or ID with rules and links
  async findOneCommunity(slugOrId: string): Promise<{
    community: ForumCommunity | null;
    rules: ForumCommunityRule[];
    links: ForumCommunityLink[];
  }> {
    const community = await this.communitiesRepository.findOne({
      where: [{ id: slugOrId }, { slug: slugOrId }],
    });

    if (!community) {
      return { community: null, rules: [], links: [] };
    }

    const [rules, links] = await Promise.all([
      this.rulesRepository.find({
        where: { communityId: community.id },
        order: { sortOrder: 'ASC' },
      }),
      this.linksRepository.find({
        where: { communityId: community.id },
        order: { sortOrder: 'ASC' },
      }),
    ]);

    return { community, rules, links };
  }

  // Get posts by community slug
  async findPostsByCommunity(
    communitySlug: string,
    _options?: { sortBy?: 'hot' | 'new' | 'top' },
  ): Promise<ForumPost[]> {
    const community = await this.communitiesRepository.findOne({
      where: { slug: communitySlug },
    });

    if (!community) {
      return [];
    }

    const posts = await this.postsRepository.find({
      where: { communityId: community.id },
      relations: ['author', 'community'],
      order: { createdAt: 'DESC' },
    });

    // Get vote counts for all posts
    const postIds = posts.map((p) => p.id);
    const voteMap = await this.voteService.getVoteCountsBatch(
      EdgeOperationTargetType.FORUM_POST,
      postIds,
    );
    const commentCounts = await this.getCommentCounts(postIds);

    return posts.map((post) =>
      this.normalizePost(post, {
        commentsCount: commentCounts.get(post.id) ?? 0,
        votes: voteMap.get(post.id) || { likes: 0, dislikes: 0 },
      }),
    );
  }

  // Tag management
  async findAllTags(): Promise<ForumTag[]> {
    return this.tagsRepository.find({
      order: { usageCount: 'DESC' },
    });
  }

  // Membership operations
  async joinCommunity(
    userId: string,
    communityId: string,
  ): Promise<ForumCommunityMember> {
    // Check if already a member
    const existing = await this.membersRepository.findOne({
      where: { userId, communityId },
    });

    if (existing) {
      return existing;
    }

    const member = this.membersRepository.create({
      id: randomUUID(),
      userId,
      communityId,
      role: 'MEMBER',
      joinedAt: new Date(),
    });

    await this.membersRepository.save(member);

    // Increment member count
    await this.communitiesRepository.increment(
      { id: communityId },
      'members',
      1,
    );

    return member;
  }

  async leaveCommunity(userId: string, communityId: string): Promise<void> {
    const member = await this.membersRepository.findOne({
      where: { userId, communityId },
    });

    if (member) {
      await this.membersRepository.delete({ id: member.id });
      await this.communitiesRepository.decrement(
        { id: communityId },
        'members',
        1,
      );
    }
  }

  async checkMembership(userId: string, communityId: string): Promise<boolean> {
    const count = await this.membersRepository.count({
      where: { userId, communityId },
    });
    return count > 0;
  }

  async createComment(
    postId: string,
    body: string,
    parentId: string | null,
    author: { id: string; username: string; avatar?: string | null },
  ): Promise<ForumComment> {
    const post = await this.postsRepository.findOne({
      where: { id: postId },
    });
    if (!post) {
      throw new NotFoundException('Post not found');
    }

    await this.ensureForumUser(author);

    const comment = this.commentsRepository.create({
      id: randomUUID(),
      postId,
      body,
      parentId,
      authorId: author.id,
      createdAt: new Date(),
    });

    const saved = await this.commentsRepository.save(comment);

    const commentCount = await this.commentsRepository.count({
      where: { postId },
    });
    const updatedStats = {
      ...(post.stats ?? {}),
      comments: commentCount,
    };
    await this.postsRepository.update({ id: postId }, { stats: updatedStats });

    return saved;
  }

  async updateComment(
    commentId: string,
    body: string,
    userId: string,
  ): Promise<ForumComment> {
    const comment = await this.commentsRepository.findOne({
      where: { id: commentId },
      relations: ['author'],
    });
    if (!comment) {
      throw new NotFoundException('Comment not found');
    }
    if (comment.authorId !== userId) {
      throw new ForbiddenException('Not allowed to edit this comment');
    }

    comment.body = body;
    comment.editedAt = new Date();

    return this.commentsRepository.save(comment);
  }

  async deleteComment(commentId: string, userId: string): Promise<void> {
    const comment = await this.commentsRepository.findOne({
      where: { id: commentId },
    });
    if (!comment) {
      throw new NotFoundException('Comment not found');
    }
    if (comment.authorId !== userId) {
      throw new ForbiddenException('Not allowed to delete this comment');
    }

    await this.commentsRepository.delete({ id: commentId });

    const post = await this.postsRepository.findOne({
      where: { id: comment.postId },
    });
    if (post) {
      const commentCount = await this.commentsRepository.count({
        where: { postId: comment.postId },
      });
      const updatedStats = {
        ...(post.stats ?? {}),
        comments: commentCount,
      };
      await this.postsRepository.update(
        { id: comment.postId },
        { stats: updatedStats },
      );
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
    const community = await this.communitiesRepository.findOne({
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

    const post = this.postsRepository.create({
      id: randomUUID(),
      communityId: input.communityId,
      userId: author.id,
      title: input.title,
      excerpt: input.excerpt ?? null,
      tags: input.tags ?? [],
      flairType: input.flairType ?? null,
      flairLabel: input.flairLabel ?? null,
      media: input.media ?? null,
      createdAt: new Date(),
      stats: { comments: 0, views: 0 },
    });

    const saved = await this.postsRepository.save(post);

    await this.communitiesRepository.increment(
      { id: input.communityId },
      'postsCount',
      1,
    );

    return this.normalizePost(saved, {
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
    const post = await this.postsRepository.findOne({
      where: { id: postId },
      relations: ['community'],
    });
    if (!post) {
      throw new NotFoundException('Post not found');
    }

    const isAuthor = post.userId === userId;
    const canModerate = await this.canModerateCommunity(
      userId,
      post.communityId,
    );

    if (!isAuthor && !canModerate) {
      throw new ForbiddenException('Not allowed to edit this post');
    }

    const wantsModeration =
      input.isPinned !== undefined || input.isLocked !== undefined;

    if (wantsModeration && !canModerate) {
      throw new ForbiddenException('Not allowed to manage this post');
    }

    if (isAuthor) {
      if (input.title !== undefined) post.title = input.title;
      if (input.excerpt !== undefined) post.excerpt = input.excerpt;
      if (input.tags !== undefined) post.tags = input.tags;
      if (input.flairType !== undefined) post.flairType = input.flairType;
      if (input.flairLabel !== undefined) post.flairLabel = input.flairLabel;
      if (input.media !== undefined) post.media = input.media;
    }

    if (input.isPinned !== undefined) post.isPinned = input.isPinned;
    if (input.isLocked !== undefined) post.isLocked = input.isLocked;

    const saved = await this.postsRepository.save(post);

    return this.normalizePost(saved, {
      commentsCount: post.stats?.comments ?? 0,
      votes: { likes: 0, dislikes: 0 },
    });
  }

  async deletePost(postId: string, userId: string): Promise<void> {
    const post = await this.postsRepository.findOne({
      where: { id: postId },
    });
    if (!post) {
      throw new NotFoundException('Post not found');
    }

    const isAuthor = post.userId === userId;
    const canModerate = await this.canModerateCommunity(
      userId,
      post.communityId,
    );

    if (!isAuthor && !canModerate) {
      throw new ForbiddenException('Not allowed to delete this post');
    }

    await this.postsRepository.delete({ id: postId });
    await this.communitiesRepository.decrement(
      { id: post.communityId },
      'postsCount',
      1,
    );
  }

  async findPostsByUser(userId: string): Promise<ForumPost[]> {
    const posts = await this.postsRepository.find({
      where: { userId },
      relations: ['author', 'community'],
      order: { createdAt: 'DESC' },
    });
    const postIds = posts.map((p) => p.id);
    const voteMap = await this.voteService.getVoteCountsBatch(
      EdgeOperationTargetType.FORUM_POST,
      postIds,
    );
    const commentCounts = await this.getCommentCounts(postIds);

    return posts.map((post) =>
      this.normalizePost(post, {
        commentsCount: commentCounts.get(post.id) ?? 0,
        votes: voteMap.get(post.id) || { likes: 0, dislikes: 0 },
      }),
    );
  }
}
