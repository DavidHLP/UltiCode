import { Injectable } from '@nestjs/common';
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
    private readonly voteService: VoteService,
  ) {}

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

    return posts.map((post) => {
      const stats: { likes: number; dislikes: number } = voteMap.get(
        post.id,
      ) || { likes: 0, dislikes: 0 };
      // Inject vote counts into the post object (need to cast or extend type)
      return {
        ...post,
        likes: stats.likes,
        dislikes: stats.dislikes,
        score: stats.likes - stats.dislikes,
      } as unknown as ForumPost;
    });
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

    return {
      ...post,
      likes: stats.likes,
      dislikes: stats.dislikes,
      score: stats.likes - stats.dislikes,
    } as unknown as ForumPost;
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
          userVote: commentUserVoteMap.get(comment.id) || 0,
        };
      });

      return {
        ...post,
        likes: postStats.likes,
        dislikes: postStats.dislikes,
        score: postStats.likes - postStats.dislikes,
        userVote: postUserVote as 0 | 1 | -1,
        voteState:
          postUserVote === 1
            ? 'upvoted'
            : postUserVote === -1
              ? 'downvoted'
              : 'neutral',
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

    return posts.map((post) => {
      const stats = voteMap.get(post.id) || { likes: 0, dislikes: 0 };
      return {
        ...post,
        likes: stats.likes,
        dislikes: stats.dislikes,
        score: stats.likes - stats.dislikes,
      } as unknown as ForumPost;
    });
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
    authorId: string,
  ): Promise<ForumComment> {
    const comment = this.commentsRepository.create({
      id: randomUUID(),
      postId,
      body,
      parentId,
      authorId,
      createdAt: new Date(),
    });

    return this.commentsRepository.save(comment);
  }
}
