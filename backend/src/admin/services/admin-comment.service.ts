import { Injectable } from '@nestjs/common';
import {
  Prisma,
  type ForumComment,
  type SolutionComment,
} from '@prisma/client';
import { PrismaService } from '../../prisma.service';
import { CommentQueryDto, CommentType } from '../dto/comment.dto';
import { AuditService } from './audit.service';

@Injectable()
export class AdminCommentService {
  constructor(
    private prisma: PrismaService,
    private auditService: AuditService,
  ) {}

  async findAll(query: CommentQueryDto) {
    const {
      page = 1,
      limit = 20,
      type,
      search,
      is_flagged,
      is_deleted,
      sortBy = 'created_at',
      sortOrder = 'desc',
    } = query;
    const skip = (page - 1) * limit;

    const baseWhere: Prisma.ForumCommentWhereInput &
      Prisma.SolutionCommentWhereInput = {};

    if (is_flagged !== undefined) {
      baseWhere.is_flagged = is_flagged;
    }

    if (is_deleted !== undefined) {
      baseWhere.is_deleted = is_deleted;
    } else {
      // Default to not showing deleted unless requested
      baseWhere.is_deleted = false;
    }

    const sortField: Prisma.ForumCommentScalarFieldEnum &
      Prisma.SolutionCommentScalarFieldEnum =
      sortBy as Prisma.ForumCommentScalarFieldEnum &
        Prisma.SolutionCommentScalarFieldEnum;
    const orderBy: Prisma.ForumCommentOrderByWithRelationInput &
      Prisma.SolutionCommentOrderByWithRelationInput = {
      [sortField]: sortOrder,
    };

    type ForumCommentWithRelations = Prisma.ForumCommentGetPayload<{
      include: {
        author: { select: { id: true; username: true; avatar: true } };
        post: { select: { id: true; title: true } };
      };
    }>;

    type SolutionCommentWithRelations = Prisma.SolutionCommentGetPayload<{
      include: {
        author: { select: { id: true; username: true; avatar: true } };
        solution: { select: { id: true; title: true } };
      };
    }>;

    let forumComments: ForumCommentWithRelations[] = [];
    let solutionComments: SolutionCommentWithRelations[] = [];
    let totalForum = 0;
    let totalSolution = 0;

    // If type is specified or 'all', fetch accordingly
    if (!type || type === CommentType.FORUM) {
      const forumWhere: Prisma.ForumCommentWhereInput = { ...baseWhere };
      if (search) {
        forumWhere.body = { contains: search };
      }

      const [fData, fCount] = await Promise.all([
        this.prisma.forumComment.findMany({
          where: forumWhere,
          include: {
            author: { select: { id: true, username: true, avatar: true } },
            post: { select: { id: true, title: true } },
          },
          orderBy,
          take: type === CommentType.FORUM ? limit : Math.floor(limit / 2), // Simple split if both
          skip: type === CommentType.FORUM ? skip : 0,
        }),
        this.prisma.forumComment.count({ where: forumWhere }),
      ]);
      forumComments = fData;
      totalForum = fCount;
    }

    if (!type || type === CommentType.SOLUTION) {
      const solutionWhere: Prisma.SolutionCommentWhereInput = { ...baseWhere };
      if (search) {
        solutionWhere.content = { contains: search };
      }

      const [sData, sCount] = await Promise.all([
        this.prisma.solutionComment.findMany({
          where: solutionWhere,
          include: {
            author: { select: { id: true, username: true, avatar: true } },
            solution: { select: { id: true, title: true } },
          },
          orderBy,
          take: type === CommentType.SOLUTION ? limit : Math.floor(limit / 2),
          skip: type === CommentType.SOLUTION ? skip : 0,
        }),
        this.prisma.solutionComment.count({ where: solutionWhere }),
      ]);
      solutionComments = sData;
      totalSolution = sCount;
    }

    // Transform and merge
    const transformedForum = forumComments.map((c) => ({
      ...c,
      content: c.body,
      type: 'forum' as const,
      parentId: c.post_id,
      parentTitle: c.post?.title,
    }));

    const transformedSolution = solutionComments.map((c) => ({
      ...c,
      type: 'solution' as const,
      parentId: c.solution_id,
      parentTitle: c.solution?.title,
    }));

    const data = [...transformedForum, ...transformedSolution];

    // Re-sort if mixed
    if (!type) {
      data.sort((a, b) => {
        const rawA = a[sortField];
        const rawB = b[sortField];
        const dateA = rawA instanceof Date ? rawA.getTime() : 0;
        const dateB = rawB instanceof Date ? rawB.getTime() : 0;
        return sortOrder === 'desc' ? dateB - dateA : dateA - dateB;
      });
    }

    return {
      data,
      meta: {
        total: totalForum + totalSolution,
        page,
        limit,
        totalPages: Math.ceil((totalForum + totalSolution) / limit),
      },
    };
  }

  async flag(
    id: string,
    type: CommentType,
    reason: string,
    adminId: string,
  ): Promise<ForumComment | SolutionComment> {
    let comment: ForumComment | SolutionComment;
    if (type === CommentType.FORUM) {
      comment = await this.prisma.forumComment.update({
        where: { id },
        data: {
          is_flagged: true,
          flagged_reason: reason,
          flagged_at: new Date(),
        },
      });
    } else {
      comment = await this.prisma.solutionComment.update({
        where: { id },
        data: {
          is_flagged: true,
          flagged_reason: reason,
          flagged_at: new Date(),
        },
      });
    }

    await this.auditService.log({
      performerId: adminId,
      action: 'FLAG_COMMENT',
      entityType:
        type === CommentType.FORUM ? 'forum_comment' : 'solution_comment',
      entityId: id,
      newValues: { reason },
    });

    return comment;
  }

  async unflag(
    id: string,
    type: CommentType,
    adminId: string,
  ): Promise<ForumComment | SolutionComment> {
    let comment: ForumComment | SolutionComment;
    if (type === CommentType.FORUM) {
      comment = await this.prisma.forumComment.update({
        where: { id },
        data: {
          is_flagged: false,
          flagged_reason: null,
          flagged_at: null,
        },
      });
    } else {
      comment = await this.prisma.solutionComment.update({
        where: { id },
        data: {
          is_flagged: false,
          flagged_reason: null,
          flagged_at: null,
        },
      });
    }

    await this.auditService.log({
      performerId: adminId,
      action: 'UNFLAG_COMMENT',
      entityType:
        type === CommentType.FORUM ? 'forum_comment' : 'solution_comment',
      entityId: id,
    });

    return comment;
  }

  async softDelete(
    id: string,
    type: CommentType,
    adminId: string,
  ): Promise<ForumComment | SolutionComment> {
    let comment: ForumComment | SolutionComment;
    if (type === CommentType.FORUM) {
      comment = await this.prisma.forumComment.update({
        where: { id },
        data: {
          is_deleted: true,
          deleted_at: new Date(),
          deleted_by: adminId,
        },
      });
    } else {
      comment = await this.prisma.solutionComment.update({
        where: { id },
        data: {
          is_deleted: true,
          deleted_at: new Date(),
          deleted_by: adminId,
        },
      });
    }

    await this.auditService.log({
      performerId: adminId,
      action: 'DELETE_COMMENT',
      entityType:
        type === CommentType.FORUM ? 'forum_comment' : 'solution_comment',
      entityId: id,
    });

    return comment;
  }
}
