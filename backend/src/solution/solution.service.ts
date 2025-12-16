/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import type { SolutionFeedResponse } from './dto/solution-feed.dto';
import type { CreateSolutionCommentDto } from './dto/create-solution-comment.dto';
import type { VoteSolutionDto } from './dto/vote-solution.dto';
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

  async voteSolution(solutionId: string, dto: VoteSolutionDto) {
    // Check if vote exists
    const existingVote = await this.prisma.solutionVote.findUnique({
      where: {
        solution_id_user_id: {
          solution_id: solutionId,
          user_id: dto.userId,
        },
      },
    });

    if (existingVote) {
      if (existingVote.vote_type === dto.voteType) {
        // Toggle off (remove vote)
        await this.prisma.solutionVote.delete({
          where: {
            solution_id_user_id: {
              solution_id: solutionId,
              user_id: dto.userId,
            },
          },
        });
      } else {
        // Change vote
        await this.prisma.solutionVote.update({
          where: {
            solution_id_user_id: {
              solution_id: solutionId,
              user_id: dto.userId,
            },
          },
          data: {
            vote_type: dto.voteType,
          },
        });
      }
    } else {
      // Create new vote
      await this.prisma.solutionVote.create({
        data: {
          solution_id: solutionId,
          user_id: dto.userId,
          vote_type: dto.voteType,
        },
      });
    }

    // Recalculate and update solution stats (optional but recommended for performance)
    // For simplicity, we can fetch count or rely on client to update optimistic
    // But keeping DB in sync is good.
    // Let's create an aggregation
    const upvotes = await this.prisma.solutionVote.count({
      where: { solution_id: solutionId, vote_type: 1 },
    });
    const downvotes = await this.prisma.solutionVote.count({
      where: { solution_id: solutionId, vote_type: -1 },
    });

    await this.prisma.solution.update({
      where: { id: solutionId },
      data: {
        likes: upvotes,
        dislikes: downvotes,
      },
    });

    return { likes: upvotes, dislikes: downvotes };
  }

  async voteComment(commentId: string, dto: VoteSolutionDto) {
    const existingVote = await this.prisma.solutionCommentVote.findUnique({
      where: {
        comment_id_user_id: {
          comment_id: commentId,
          user_id: dto.userId,
        },
      },
    });

    if (existingVote) {
      if (existingVote.vote_type === dto.voteType) {
        // Toggle off
        await this.prisma.solutionCommentVote.delete({
          where: {
            comment_id_user_id: {
              comment_id: commentId,
              user_id: dto.userId,
            },
          },
        });
      } else {
        // Change vote
        await this.prisma.solutionCommentVote.update({
          where: {
            comment_id_user_id: {
              comment_id: commentId,
              user_id: dto.userId,
            },
          },
          data: {
            vote_type: dto.voteType,
          },
        });
      }
    } else {
      // Create
      await this.prisma.solutionCommentVote.create({
        data: {
          comment_id: commentId,
          user_id: dto.userId,
          vote_type: dto.voteType,
        },
      });
    }

    const upvotes = await this.prisma.solutionCommentVote.count({
      where: { comment_id: commentId, vote_type: 1 },
    });
    const downvotes = await this.prisma.solutionCommentVote.count({
      where: { comment_id: commentId, vote_type: -1 },
    });

    await this.prisma.solutionComment.update({
      where: { id: commentId },
      data: {
        likes: upvotes,
        dislikes: downvotes,
      },
    });

    return { likes: upvotes, dislikes: downvotes };
  }
}
