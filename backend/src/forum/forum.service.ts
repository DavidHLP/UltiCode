import { Injectable } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { ForumPost } from './entities/post.entity';
import { ForumCommunity } from './entities/community.entity';
import { ForumComment } from './entities/comment.entity';
import { VoteService } from '../vote/vote.service';
import { VoteTargetType } from '@prisma/client';

@Injectable()
export class ForumService {
  constructor(
    @InjectRepository(ForumPost)
    private postsRepository: Repository<ForumPost>,
    @InjectRepository(ForumCommunity)
    private communitiesRepository: Repository<ForumCommunity>,
    @InjectRepository(ForumComment)
    private commentsRepository: Repository<ForumComment>,
    private readonly voteService: VoteService,
  ) {}

  async findAllPosts(): Promise<ForumPost[]> {
    const posts = await this.postsRepository.find({
      relations: ['author', 'community'],
      order: { createdAt: 'DESC' },
    });

    const postIds = posts.map((p) => p.id);
    const voteMap = await this.voteService.getVoteCountsBatch(
      VoteTargetType.FORUM_POST,
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
      await this.voteService.getVoteCounts(VoteTargetType.FORUM_POST, id);

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
        VoteTargetType.FORUM_POST,
        id,
      );

      // 2. Fetch Post User Vote
      let postUserVote = 0;
      if (userId) {
        const votes = await this.voteService.getUserVotesBatch(
          userId,
          VoteTargetType.FORUM_POST,
          [id],
        );
        postUserVote = votes.get(id) || 0;
      }

      // 3. Fetch Comment Stats (Batch)
      const commentIds = comments.map((c) => c.id);
      const commentVoteMap = await this.voteService.getVoteCountsBatch(
        VoteTargetType.FORUM_COMMENT,
        commentIds,
      );

      // 4. Fetch Comment User Votes (Batch)
      let commentUserVoteMap = new Map<string, number>();
      if (userId) {
        commentUserVoteMap = await this.voteService.getUserVotesBatch(
          userId,
          VoteTargetType.FORUM_COMMENT,
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

  async findAllCommunities(): Promise<ForumCommunity[]> {
    return this.communitiesRepository.find();
  }

  async createComment(
    postId: string,
    body: string,
    parentId: string | null,
  ): Promise<ForumComment> {
    const comment = this.commentsRepository.create({
      id: randomUUID(),
      postId,
      body,
      parentId,
      authorId: 'u-001', // Default to Shadcn ID
      createdAt: new Date(),
    });

    return this.commentsRepository.save(comment);
  }
}
