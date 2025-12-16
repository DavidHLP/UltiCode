/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import type { SolutionFeedResponse } from './dto/solution-feed.dto';
import type { CreateSolutionCommentDto } from './dto/create-solution-comment.dto';

import { v4 as uuidv4 } from 'uuid';

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
      },
      orderBy: {
        created_at: 'desc',
      },
    });

    const items = solutions.map((solution) => {
      // Use pre-aggregated stats
      const upvotes = solution.likes || 0;
      const downvotes = solution.dislikes || 0;

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
      },
      orderBy: {
        created_at: 'desc',
      },
    });

    const items = solutions.map((solution) => {
      const upvotes = solution.likes || 0;
      const downvotes = solution.dislikes || 0;

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

  async findComments(solutionId: string) {
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

    // Map to frontend expected format (similar to forum comments)
    return comments.map((comment) => ({
      id: comment.id,
      parentId: comment.parent_id,
      body: comment.content,
      upvotes: comment.likes,
      createdAt: comment.created_at,
      author: {
        username: comment.author.username,
        avatar: comment.author.avatar,
      },
    }));
  }

  async createComment(solutionId: string, dto: CreateSolutionCommentDto) {
    return this.prisma.solutionComment.create({
      data: {
        id: uuidv4(),
        solution_id: solutionId,
        content: dto.content,
        parent_id: dto.parentId,
        user_id: dto.userId,
      },
      include: {
        author: true,
      },
    });
  }
}
