import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { VoteService } from '../../vote/vote.service';
import {
  Solution,
  User,
  SolutionComment,
  Problem,
  EdgeOperationTargetType,
} from '@prisma/client';
import type { SolutionFeedResponse } from '../dto/solution-feed.dto';

const TOPIC_MAP: Record<string, string> = {
  algorithms: 'Algorithms',
  database: 'Database',
  shell: 'Shell',
  concurrency: 'Concurrency',
  'system-design': 'System Design',
  javascript: 'JavaScript',
  python: 'Python',
};

@Injectable()
export class SolutionQueryService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly voteService: VoteService,
  ) {}

  async findOne(id: string, userId?: string) {
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

    const userVote = userId
      ? await this.voteService.getUserVote(
          userId,
          EdgeOperationTargetType.SOLUTION,
          id,
        )
      : 0;

    return this.mapToFeedItem(
      solution,
      votes.get(id) || { likes: 0, dislikes: 0 },
      userVote,
      userId,
    );
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

    const solutionIds = solutions.map((s) => s.id);
    const voteMap = await this.voteService.getVoteCountsBatch(
      EdgeOperationTargetType.SOLUTION,
      solutionIds,
    );

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
}
