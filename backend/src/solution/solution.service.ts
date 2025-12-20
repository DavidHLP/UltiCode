import { Injectable, BadRequestException } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { VoteService } from '../vote/vote.service';
import { VoteTargetType } from '@prisma/client';
import type { SolutionFeedResponse } from './dto/solution-feed.dto';
import type { CreateSolutionDto } from './dto/create-solution.dto';
import type { CreateSolutionCommentDto } from './dto/create-solution-comment.dto';

import { v4 as uuidv4 } from 'uuid';

@Injectable()
export class SolutionService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly voteService: VoteService,
  ) {}

  async create(problemId: string, userId: string, dto: CreateSolutionDto) {
    console.log(
      `[CreateSolution] Checking submission for problem: ${problemId}, user: ${userId}`,
    );
    const submission = await this.prisma.submission.findFirst({
      where: {
        problem_id: BigInt(problemId),
        user_id: userId,
        status: 'Accepted',
      },
    });
    console.log('[CreateSolution] Submission found:', submission);

    if (!submission) {
      throw new BadRequestException(
        'You must have an accepted submission to create a solution',
      );
    }

    return this.prisma.solution.create({
      data: {
        id: uuidv4(),
        problem_id: BigInt(problemId),
        user_id: userId,
        title: dto.title,
        content: dto.content,
        language: dto.language,
        tags: dto.tags ?? [],
      },
    });
  }

  async findByProblemId(
    problemId: string,
    userId?: string,
  ): Promise<SolutionFeedResponse> {
    const solutions = await this.prisma.solution.findMany({
      where: {
        problem_id: BigInt(problemId),
      },
      include: {
        author: true,
        comments: true,
      },
      orderBy: {
        created_at: 'desc',
      },
    });

    // Batch fetch votes
    const solutionIds = solutions.map((s) => s.id);
    const voteMap = await this.voteService.getVoteCountsBatch(
      VoteTargetType.SOLUTION,
      solutionIds,
    );

    // Batch fetch user votes if userId is provided
    let userVoteMap = new Map<string, number>();
    if (userId) {
      userVoteMap = await this.voteService.getUserVotesBatch(
        userId,
        VoteTargetType.SOLUTION,
        solutionIds,
      );
    }

    const items = solutions.map((solution) => {
      const votes = voteMap.get(solution.id) || { likes: 0, dislikes: 0 };
      const upvotes = votes.likes;
      const downvotes = votes.dislikes;
      const userVote = userVoteMap.get(solution.id) || 0;

      return {
        id: solution.id,
        title: solution.title,
        summary: solution.summary || '',
        highlight: solution.title,
        flair: '',
        badges: [],
        authorId: solution.user_id,
        author: {
          id: solution.author.id,
          username: solution.author.username,
          name: solution.author.name || solution.author.username,
          role: 'User',
          avatarColor: '#94a3b8',
          avatar: solution.author.avatar || undefined,
        },
        stats: {
          views: solution.views,
          comments: solution.comments.length,
          likes: upvotes,
          dislikes: downvotes,
        },
        userVote: userVote as 0 | 1 | -1,
        score: upvotes - downvotes,
        is_pinned: false,
        is_locked: false,
        created_at: solution.created_at.toISOString(),
        publishedAt: solution.created_at.toISOString(),
        language: solution.language,
        languageFilter: solution.language.toLowerCase(),
        topic: {
          id: 'general',
          name: 'General',
        },
        topicName: 'General',
        content: solution.content,
        tags: (Array.isArray(solution.tags) ? solution.tags : []) as string[],
        votes: upvotes,
        views: solution.views,
        likes: upvotes,
        dislikes: downvotes,
        comments: solution.comments.length,
      };
    });

    return {
      items,
      total: items.length,
      sortOptions: [
        { label: 'Most liked', value: 'likes' },
        { label: 'Most recent', value: 'newest' },
        { label: 'Oldest', value: 'oldest' },
        { label: 'Hot', value: 'heat' },
      ],
    };
  }
  async findAllByUser(userId: string): Promise<SolutionFeedResponse> {
    const solutions = await this.prisma.solution.findMany({
      where: {
        user_id: userId,
      },
      include: {
        author: true,
        comments: true,
      },
      orderBy: {
        created_at: 'desc',
      },
    });

    // Batch fetch votes
    const solutionIds = solutions.map((s) => s.id);
    const voteMap = await this.voteService.getVoteCountsBatch(
      VoteTargetType.SOLUTION,
      solutionIds,
    );

    const items = solutions.map((solution) => {
      const votes = voteMap.get(solution.id) || { likes: 0, dislikes: 0 };
      const upvotes = votes.likes;
      const downvotes = votes.dislikes;

      return {
        id: solution.id,
        title: solution.title,
        summary: solution.summary || '',
        highlight: solution.title,
        flair: '',
        badges: [],
        authorId: solution.user_id,
        author: {
          id: solution.author.id,
          username: solution.author.username,
          name: solution.author.name || solution.author.username,
          role: 'User',
          avatarColor: '#94a3b8',
          avatar: solution.author.avatar || undefined,
        },
        stats: {
          views: solution.views,
          comments: solution.comments.length,
          likes: upvotes,
        },
        score: upvotes - downvotes,
        is_pinned: false,
        is_locked: false,
        created_at: solution.created_at.toISOString(),
        publishedAt: solution.created_at.toISOString(),
        language: solution.language,
        languageFilter: solution.language.toLowerCase(),
        topic: {
          id: 'general',
          name: 'General',
        },
        topicName: 'General',
        content: solution.content,
        tags: (Array.isArray(solution.tags) ? solution.tags : []) as string[],
        votes: upvotes,
        views: solution.views,
        likes: upvotes,
        comments: solution.comments.length,
      };
    });

    return {
      items,
      total: items.length,
      sortOptions: [
        { label: 'Most liked', value: 'likes' },
        { label: 'Most recent', value: 'newest' },
        { label: 'Oldest', value: 'oldest' },
        { label: 'Hot', value: 'heat' },
      ],
    };
  }

  async findComments(solutionId: string, userId?: string) {
    const comments = await this.prisma.solutionComment.findMany({
      where: {
        solution_id: solutionId,
      },
      include: {
        author: true,
      },
      orderBy: {
        created_at: 'asc',
      },
    });

    // Batch fetch votes for comments
    const commentIds = comments.map((c) => c.id);
    const voteMap = await this.voteService.getVoteCountsBatch(
      VoteTargetType.SOLUTION_COMMENT,
      commentIds,
    );

    // Batch fetch user votes if userId is provided
    let userVoteMap = new Map<string, number>();
    if (userId) {
      userVoteMap = await this.voteService.getUserVotesBatch(
        userId,
        VoteTargetType.SOLUTION_COMMENT,
        commentIds,
      );
    }

    // Map to frontend expected format (similar to forum comments)
    return comments.map((comment) => {
      const votes = voteMap.get(comment.id) || { likes: 0, dislikes: 0 };
      const userVote = userVoteMap.get(comment.id) || 0;
      return {
        id: comment.id,
        parentId: comment.parent_id,
        body: comment.content,
        upvotes: votes.likes,
        likes: votes.likes,
        dislikes: votes.dislikes,
        userVote: userVote as 0 | 1 | -1,
        createdAt: comment.created_at,
        author: {
          username: comment.author.username,
          avatar: comment.author.avatar,
        },
      };
    });
  }

  async createComment(
    solutionId: string,
    dto: CreateSolutionCommentDto,
    userId: string,
  ) {
    return this.prisma.solutionComment.create({
      data: {
        id: uuidv4(),
        solution_id: solutionId,
        content: dto.content,
        parent_id: dto.parentId,
        user_id: userId,
      },
      include: {
        author: true,
      },
    });
  }
}
