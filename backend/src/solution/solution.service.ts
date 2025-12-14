import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import type { SolutionFeedResponse } from './dto/solution-feed.dto';

@Injectable()
export class SolutionService {
  constructor(private readonly prisma: PrismaService) {}

  async findByProblemId(problemId: string): Promise<SolutionFeedResponse> {
    const solutions = await this.prisma.solution.findMany({
      where: {
        problem_id: BigInt(problemId),
      },
      include: {
        author: true,
        comments: true,
        votes: true,
      },
      orderBy: {
        created_at: 'desc',
      },
    });

    const items = solutions.map((solution) => {
      const upvotes = solution.votes.filter((v) => v.vote_type === 1).length;
      const downvotes = solution.votes.filter((v) => v.vote_type === -1).length;

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
  async findAllByUser(userId: string): Promise<SolutionFeedResponse> {
    const solutions = await this.prisma.solution.findMany({
      where: {
        user_id: userId,
      },
      include: {
        author: true,
        comments: true,
        votes: true,
      },
      orderBy: {
        created_at: 'desc',
      },
    });

    const items = solutions.map((solution) => {
      const upvotes = solution.votes.filter((v) => v.vote_type === 1).length;
      const downvotes = solution.votes.filter((v) => v.vote_type === -1).length;

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
}
