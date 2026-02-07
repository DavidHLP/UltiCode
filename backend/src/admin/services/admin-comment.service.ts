import { Injectable } from '@nestjs/common';
import {
  Prisma,
  type ForumComment,
  type SolutionComment,
} from '@prisma/client';
import { PrismaService } from '../../prisma.service';
import { ModerationService } from '../../common/services/moderation.service';
import { CommentQueryDto, CommentType } from '../dto/comment.dto';

/**
 * AdminCommentService - 管理员评论管理
 *
 * 职责:
 * - 查询和管理论坛评论和题解评论
 * - 使用 ModerationService 处理标记、软删除等操作
 *
 * 通过 ModerationService 统一处理审核操作，减少代码重复
 */
@Injectable()
export class AdminCommentService {
  constructor(
    private prisma: PrismaService,
    private moderationService: ModerationService,
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

    // 使用 ModerationService 的默认过滤
    const baseWhere = this.moderationService.applyDefaultModerationFilter<
      Prisma.ForumCommentWhereInput & Prisma.SolutionCommentWhereInput
    >({}, is_deleted);

    if (is_flagged !== undefined) {
      baseWhere.is_flagged = is_flagged;
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
    const model =
      type === CommentType.FORUM ? 'forumComment' : 'solutionComment';
    const entityType =
      type === CommentType.FORUM ? 'forum_comment' : 'solution_comment';

    return this.moderationService.flag(
      model,
      id,
      reason,
      adminId,
      entityType,
    ) as Promise<ForumComment | SolutionComment>;
  }

  async unflag(
    id: string,
    type: CommentType,
    adminId: string,
  ): Promise<ForumComment | SolutionComment> {
    const model =
      type === CommentType.FORUM ? 'forumComment' : 'solutionComment';
    const entityType =
      type === CommentType.FORUM ? 'forum_comment' : 'solution_comment';

    return this.moderationService.unflag(
      model,
      id,
      adminId,
      entityType,
    ) as Promise<ForumComment | SolutionComment>;
  }

  async softDelete(
    id: string,
    type: CommentType,
    adminId: string,
  ): Promise<ForumComment | SolutionComment> {
    const model =
      type === CommentType.FORUM ? 'forumComment' : 'solutionComment';
    const entityType =
      type === CommentType.FORUM ? 'forum_comment' : 'solution_comment';

    return this.moderationService.softDelete(
      model,
      id,
      adminId,
      entityType,
    ) as Promise<ForumComment | SolutionComment>;
  }

  async restore(
    id: string,
    type: CommentType,
    adminId: string,
  ): Promise<ForumComment | SolutionComment> {
    const model =
      type === CommentType.FORUM ? 'forumComment' : 'solutionComment';
    const entityType =
      type === CommentType.FORUM ? 'forum_comment' : 'solution_comment';

    return this.moderationService.restore(
      model,
      id,
      adminId,
      entityType,
    ) as Promise<ForumComment | SolutionComment>;
  }
}
