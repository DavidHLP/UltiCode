import {
  Injectable,
  BadRequestException,
  NotFoundException,
  ForbiddenException,
} from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { VoteService } from '../vote/vote.service';
import {
  Solution,
  User,
  SolutionComment,
  EdgeOperationTargetType,
  Problem,
} from '@prisma/client';
import type { SolutionFeedResponse } from './dto/solution-feed.dto';
import type { CreateSolutionDto } from './dto/create-solution.dto';
import type { CreateSolutionCommentDto } from './dto/create-solution-comment.dto';

import { v4 as uuidv4 } from 'uuid';

const TOPIC_MAP: Record<string, string> = {
  algorithms: 'Algorithms',
  database: 'Database',
  shell: 'Shell',
  concurrency: 'Concurrency',
  'system-design': 'System Design',
  javascript: 'JavaScript',
  python: 'Python',
};

const MAX_SUMMARY_LENGTH = 180;

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

    const existing = await this.prisma.solution.findFirst({
      where: {
        problem_id: BigInt(problemId),
        user_id: userId,
      },
      select: { id: true },
    });

    if (existing) {
      throw new BadRequestException(
        'Solution already exists. Please edit your existing solution.',
      );
    }

    const summary = this.buildSummary(dto.content);

    return this.prisma.$transaction(async (tx) => {
      const solution = await tx.solution.create({
        data: {
          id: uuidv4(),
          problem_id: BigInt(problemId),
          user_id: userId,
          title: dto.title,
          content: dto.content,
          summary,
          language: dto.language,
          tags: dto.tags ?? [],
        },
      });

      await tx.problem.update({
        where: { id: BigInt(problemId) },
        data: { has_solution: true },
      });

      return solution;
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
        problem: true,
      },
      orderBy: {
        created_at: 'desc',
      },
    });

    // Batch fetch votes
    const solutionIds = solutions.map((s) => s.id);
    const voteMap = await this.voteService.getVoteCountsBatch(
      EdgeOperationTargetType.SOLUTION,
      solutionIds,
    );

    // Batch fetch user votes if userId is provided
    let userVoteMap = new Map<string, number>();
    if (userId) {
      userVoteMap = await this.voteService.getUserVotesBatch(
        userId,
        EdgeOperationTargetType.SOLUTION,
        solutionIds,
      );
    }

    const items = solutions.map((solution) => {
      const votes = voteMap.get(solution.id) || { likes: 0, dislikes: 0 };
      const userVote = userVoteMap.get(solution.id) || 0;
      return this.mapToFeedItem(solution, votes, userVote, userId);
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
  async findAllByUser(
    userId: string,
    problemId?: string,
  ): Promise<SolutionFeedResponse> {
    const solutions = await this.prisma.solution.findMany({
      where: {
        user_id: userId,
        ...(problemId ? { problem_id: BigInt(problemId) } : {}),
      },
      include: {
        author: true,
        comments: true,
        problem: true,
      },
      orderBy: {
        created_at: 'desc',
      },
    });

    // Batch fetch votes
    const solutionIds = solutions.map((s) => s.id);
    const voteMap = await this.voteService.getVoteCountsBatch(
      EdgeOperationTargetType.SOLUTION,
      solutionIds,
    );

    const items = solutions.map((solution) => {
      const votes = voteMap.get(solution.id) || { likes: 0, dislikes: 0 };
      return this.mapToFeedItem(solution, votes, 0, userId);
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
      EdgeOperationTargetType.SOLUTION_COMMENT,
      commentIds,
    );

    // Batch fetch user votes if userId is provided
    let userVoteMap = new Map<string, number>();
    if (userId) {
      userVoteMap = await this.voteService.getUserVotesBatch(
        userId,
        EdgeOperationTargetType.SOLUTION_COMMENT,
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
          id: comment.author.id,
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

  async findOne(id: string) {
    const solution = await this.prisma.solution.findUnique({
      where: { id },
      include: {
        author: true,
        comments: true,
        problem: true,
      },
    });

    if (!solution) {
      return null;
    }

    const votes = await this.voteService.getVoteCountsBatch(
      EdgeOperationTargetType.SOLUTION,
      [id],
    );
    return this.mapToFeedItem(
      solution,
      votes.get(id) || { likes: 0, dislikes: 0 },
    );
  }

  private mapToFeedItem(
    solution: Solution & {
      author: User;
      comments: SolutionComment[];
      problem?: Problem;
    },
    votes: { likes: number; dislikes: number },
    userVote: number = 0,
    userId?: string,
  ) {
    const upvotes = votes.likes;
    const downvotes = votes.dislikes;
    const isOwner = userId ? solution.user_id === userId : false;

    return {
      id: solution.id,
      problem_id: solution.problem_id.toString(),
      problem: solution.problem
        ? {
            id: solution.problem.id.toString(),
            slug: solution.problem.slug,
            title: solution.problem.title,
          }
        : undefined,
      title: solution.title,
      summary: solution.summary || '',
      highlight: solution.title,
      flair: '',
      badges: [],
      authorId: solution.user_id,
      isOwner,
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
        id:
          Array.isArray(solution.tags) && typeof solution.tags[0] === 'string'
            ? solution.tags[0]
            : 'general',
        name:
          Array.isArray(solution.tags) &&
          typeof solution.tags[0] === 'string' &&
          TOPIC_MAP[solution.tags[0]]
            ? TOPIC_MAP[solution.tags[0]]
            : 'General',
      },
      topicName:
        Array.isArray(solution.tags) &&
        typeof solution.tags[0] === 'string' &&
        TOPIC_MAP[solution.tags[0]]
          ? TOPIC_MAP[solution.tags[0]]
          : 'General',
      content: solution.content,
      tags: (Array.isArray(solution.tags) ? solution.tags : []) as string[],
      votes: upvotes,
      views: solution.views,
      likes: upvotes,
      dislikes: downvotes,
      comments: solution.comments.length,
    };
  }

  async delete(id: string, userId: string) {
    const solution = await this.prisma.solution.findUnique({
      where: { id },
    });

    if (!solution) {
      throw new NotFoundException('Solution not found');
    }

    if (solution.user_id !== userId) {
      throw new ForbiddenException('You can only delete your own solutions');
    }

    const commentIds = await this.prisma.solutionComment.findMany({
      where: { solution_id: id },
      select: { id: true },
    });

    await this.prisma.$transaction(async (tx) => {
      if (commentIds.length) {
        await tx.edgeOperation.deleteMany({
          where: {
            target_type: EdgeOperationTargetType.SOLUTION_COMMENT,
            target_id: { in: commentIds.map((comment) => comment.id) },
          },
        });
      }

      await tx.edgeOperation.deleteMany({
        where: {
          target_type: EdgeOperationTargetType.SOLUTION,
          target_id: id,
        },
      });

      await tx.solution.delete({
        where: { id },
      });
    });

    const remaining = await this.prisma.solution.count({
      where: { problem_id: solution.problem_id },
    });

    if (remaining === 0) {
      await this.prisma.problem.update({
        where: { id: solution.problem_id },
        data: { has_solution: false },
      });
    }

    return { success: true };
  }

  async update(id: string, userId: string, dto: CreateSolutionDto) {
    const solution = await this.prisma.solution.findUnique({
      where: { id },
    });

    if (!solution) {
      throw new NotFoundException('Solution not found');
    }

    if (solution.user_id !== userId) {
      throw new ForbiddenException('You can only update your own solutions');
    }

    const summary = this.buildSummary(dto.content);

    return this.prisma.solution.update({
      where: { id },
      data: {
        title: dto.title,
        content: dto.content,
        summary,
        language: dto.language,
        tags: dto.tags ?? [],
      },
    });
  }

  async updateComment(commentId: string, content: string, userId: string) {
    const comment = await this.prisma.solutionComment.findUnique({
      where: { id: commentId },
    });
    if (!comment) {
      throw new NotFoundException('Comment not found');
    }
    if (comment.user_id !== userId) {
      throw new ForbiddenException('You can only update your own comments');
    }

    return this.prisma.solutionComment.update({
      where: { id: commentId },
      data: { content },
      include: {
        author: true,
      },
    });
  }

  async deleteComment(commentId: string, userId: string) {
    const comment = await this.prisma.solutionComment.findUnique({
      where: { id: commentId },
    });
    if (!comment) {
      throw new NotFoundException('Comment not found');
    }
    if (comment.user_id !== userId) {
      throw new ForbiddenException('You can only delete your own comments');
    }

    await this.prisma.$transaction(async (tx) => {
      await tx.edgeOperation.deleteMany({
        where: {
          target_type: EdgeOperationTargetType.SOLUTION_COMMENT,
          target_id: commentId,
        },
      });

      await tx.solutionComment.delete({
        where: { id: commentId },
      });
    });

    return { success: true };
  }

  private buildSummary(content: string) {
    const plain = content
      .replace(/```[\s\S]*?```/g, '')
      .replace(/`[^`]*`/g, '')
      .replace(/!\[[^\]]*]\([^)]+\)/g, '')
      .replace(/\[[^\]]*]\([^)]+\)/g, '')
      .replace(/[#>*_~`>-]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();

    if (!plain) {
      return '';
    }

    if (plain.length <= MAX_SUMMARY_LENGTH) {
      return plain;
    }

    return `${plain.slice(0, MAX_SUMMARY_LENGTH).trim()}...`;
  }
}
