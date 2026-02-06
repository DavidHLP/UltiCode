import {
  Injectable,
  NotFoundException,
  ForbiddenException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { VoteService } from '../../vote/vote.service';
import { EdgeOperationTargetType } from '@prisma/client';
import type { CreateSolutionCommentDto } from '../dto/create-solution-comment.dto';
import { v4 as uuidv4 } from 'uuid';

@Injectable()
export class SolutionCommentService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly voteService: VoteService,
  ) {}

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

    const commentIds = comments.map((c) => c.id);
    const voteMap = await this.voteService.getVoteCountsBatch(
      EdgeOperationTargetType.SOLUTION_COMMENT,
      commentIds,
    );

    let userVoteMap = new Map<string, number>();
    if (userId) {
      userVoteMap = await this.voteService.getUserVotesBatch(
        userId,
        EdgeOperationTargetType.SOLUTION_COMMENT,
        commentIds,
      );
    }

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
    const commentId: string = uuidv4();
    return this.prisma.solutionComment.create({
      data: {
        id: commentId,
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
}
