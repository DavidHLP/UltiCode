import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { VoteService } from '../../vote/vote.service';
import { BaseCommentService } from '../../common/services/base-comment.service';
import { CommentEntityType } from '../../common/types/comment.types';
import type { CreateSolutionCommentDto } from '../dto/create-solution-comment.dto';
import { v4 as uuidv4 } from 'uuid';

/**
 * SolutionCommentService - 题解评论管理
 *
 * 职责:
 * - 创建、更新、删除题解评论
 * - 获取题解评论列表（带投票信息）
 *
 * 继承自 BaseCommentService，复用投票相关通用功能
 */
@Injectable()
export class SolutionCommentService extends BaseCommentService {
  constructor(prisma: PrismaService, voteService: VoteService) {
    super(prisma, voteService);
  }

  /**
   * 获取题解的评论列表（带投票信息）
   */
  async findComments(
    solutionId: string,
    userId?: string,
  ): Promise<
    Array<{
      id: string;
      parentId: string | null;
      body: string;
      upvotes: number;
      likes: number;
      dislikes: number;
      userVote: 0 | 1 | -1;
      createdAt: Date;
      author: {
        id: string;
        username: string;
        avatar: string | null;
      };
    }>
  > {
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

    // 使用基类的投票增强方法
    const enrichedComments = await this.enrichWithVotes(
      comments.map((comment) => ({
        id: comment.id,
        createdAt: comment.created_at,
      })),
      CommentEntityType.SOLUTION,
      userId,
    );

    // 合并评论内容
    return comments.map((comment, index) => ({
      id: comment.id,
      parentId: comment.parent_id,
      body: comment.content,
      upvotes: enrichedComments[index].likes,
      likes: enrichedComments[index].likes,
      dislikes: enrichedComments[index].dislikes,
      userVote: enrichedComments[index].userVote as 0 | 1 | -1,
      createdAt: comment.created_at,
      author: {
        id: comment.author.id,
        username: comment.author.username,
        avatar: comment.author.avatar,
      },
    }));
  }

  /**
   * 创建评论
   */
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

  /**
   * 更新评论
   */
  async updateComment(commentId: string, content: string, userId: string) {
    // 使用基类的权限验证方法
    await this.validateCommentOwnership(
      commentId,
      userId,
      CommentEntityType.SOLUTION,
      'user_id',
    );

    return this.prisma.solutionComment.update({
      where: { id: commentId },
      data: { content },
      include: {
        author: true,
      },
    });
  }

  /**
   * 删除评论
   */
  async deleteComment(commentId: string, userId: string) {
    // 使用基类的权限验证方法
    await this.validateCommentOwnership(
      commentId,
      userId,
      CommentEntityType.SOLUTION,
      'user_id',
    );

    await this.prisma.$transaction(async (tx) => {
      await tx.edgeOperation.deleteMany({
        where: {
          target_type: this.getVoteTargetType(CommentEntityType.SOLUTION),
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
