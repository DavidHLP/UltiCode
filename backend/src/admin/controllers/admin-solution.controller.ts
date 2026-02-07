import {
  Controller,
  Get,
  Post,
  Delete,
  Param,
  Body,
  Query,
  UseGuards,
} from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { RequireRoles } from '../decorators/roles.decorator';
import { CurrentAdmin } from '../decorators/current-admin.decorator';
import { AuditService } from '../services/audit.service';
import { ModerationService } from '../../common/services/moderation.service';
import { PrismaService } from '../../prisma.service';
import type { User } from '../../user/user.service';
import { UserRole } from '../../user/user.service';
import { PermissionAction, PermissionResource, Prisma } from '@prisma/client';
import {
  SolutionQueryDto,
  FlagSolutionDto,
  BulkSolutionActionDto,
} from '../dto/solution.dto';

@Controller('admin/solutions')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard, CsrfGuard)
export class AdminSolutionController {
  constructor(
    private prisma: PrismaService,
    private auditService: AuditService,
    private moderation: ModerationService,
  ) {}

  @Get()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.SOLUTION,
  })
  async findAll(@Query() query: SolutionQueryDto) {
    const {
      search,
      problemId,
      userId,
      is_flagged,
      is_published,
      is_deleted,
      page = 1,
      limit = 20,
      sortBy = 'created_at',
      sortOrder = 'desc',
    } = query;

    const skip = (page - 1) * limit;

    const where: Prisma.SolutionWhereInput = {};

    if (search) {
      where.OR = [
        { content: { contains: search } },
        { title: { contains: search } },
      ];
    }

    if (problemId) {
      where.problem_id = BigInt(problemId);
    }

    if (userId) {
      where.user_id = userId;
    }

    if (is_flagged !== undefined) {
      where.is_flagged = is_flagged;
    }

    if (is_published !== undefined) {
      where.is_published = is_published;
    }

    if (is_deleted !== undefined) {
      where.is_deleted = is_deleted;
    }

    const solutions = await this.prisma.solution.findMany({
      where,
      skip,
      take: limit,
      orderBy: { [sortBy]: sortOrder },
      include: {
        author: {
          select: {
            id: true,
            username: true,
            name: true,
          },
        },
        problem: {
          select: {
            id: true,
            slug: true,
            title: true,
            difficulty: true,
          },
        },
        _count: {
          select: {
            comments: true,
          },
        },
      },
    });
    const total = await this.prisma.solution.count({ where });

    type SolutionWithCount = Prisma.SolutionGetPayload<{
      include: {
        author: { select: { id: true; username: true; name: true } };
        problem: {
          select: { id: true; slug: true; title: true; difficulty: true };
        };
        _count: { select: { comments: true } };
      };
    }>;

    return {
      data: (solutions as SolutionWithCount[]).map((s) => ({
        ...s,
        id: s.id,
        problem_id: s.problem_id.toString(),
        user_id: s.user_id,
        comment_count: s._count.comments,
        _count: undefined,
      })),
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
  }

  @Get('flagged')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.SOLUTION,
  })
  async getFlagged(@Query() query: SolutionQueryDto) {
    const { page = 1, limit = 20 } = query;

    const skip = (page - 1) * limit;

    const where: Prisma.SolutionWhereInput = {
      is_flagged: true,
      is_deleted: false,
    };

    const solutions = await this.prisma.solution.findMany({
      where,
      skip,
      take: limit,
      orderBy: { flagged_at: 'desc' },
      include: {
        author: {
          select: {
            id: true,
            username: true,
            name: true,
          },
        },
        problem: {
          select: {
            id: true,
            slug: true,
            title: true,
          },
        },
      },
    });
    const total = await this.prisma.solution.count({ where });

    return {
      data: solutions.map((s) => ({
        ...s,
        id: s.id.toString(),
        problem_id: s.problem_id.toString(),
        user_id: s.user_id.toString(),
      })),
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
  }

  @Get(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.SOLUTION,
  })
  async findOne(@Param('id') id: string) {
    const solution = await this.prisma.solution.findUnique({
      where: { id: id },
      include: {
        author: {
          select: {
            id: true,
            username: true,
            name: true,
            email: true,
          },
        },
        problem: {
          select: {
            id: true,
            slug: true,
            title: true,
            difficulty: true,
          },
        },
        comments: {
          include: {
            author: {
              select: {
                id: true,
                username: true,
                name: true,
              },
            },
          },
          orderBy: { created_at: 'asc' },
        },
      },
    });

    if (!solution) {
      return null;
    }

    return {
      ...solution,
      id: solution.id.toString(),
      problem_id: solution.problem_id.toString(),
      user_id: solution.user_id.toString(),
    };
  }

  @Post(':id/flag')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.SOLUTION,
  })
  async flagSolution(
    @Param('id') id: string,
    @Body() flagDto: FlagSolutionDto,
    @CurrentAdmin() admin: User,
  ) {
    const solution = (await this.moderation.flag(
      'solution',
      id,
      flagDto.reason,
      admin.id,
      'SOLUTION',
    )) as {
      id: bigint;
      problem_id: bigint;
      user_id: string;
      [key: string]: unknown;
    };
    return {
      ...solution,
      id: solution.id.toString(),
      problem_id: solution.problem_id.toString(),
      user_id: solution.user_id.toString(),
    };
  }

  @Post(':id/unflag')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.SOLUTION,
  })
  async unflagSolution(@Param('id') id: string, @CurrentAdmin() admin: User) {
    const solution = (await this.moderation.unflag(
      'solution',
      id,
      admin.id,
      'SOLUTION',
    )) as {
      id: bigint;
      problem_id: bigint;
      user_id: string;
      [key: string]: unknown;
    };
    return {
      ...solution,
      id: solution.id.toString(),
      problem_id: solution.problem_id.toString(),
      user_id: solution.user_id.toString(),
    };
  }

  @Delete(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.DELETE,
    resource: PermissionResource.SOLUTION,
  })
  async remove(@Param('id') id: string, @CurrentAdmin() admin: User) {
    const oldSolution = await this.prisma.solution.findUnique({
      where: { id: id },
    });

    await this.moderation.softDelete('solution', id, admin.id, 'SOLUTION');

    await this.auditService.log({
      performerId: admin.id,
      action: 'DELETE_SOLUTION',
      entityType: 'SOLUTION',
      entityId: id,
      oldValues: {
        problem_id: oldSolution?.problem_id.toString(),
        user_id: oldSolution?.user_id,
      },
    });

    return { message: 'Solution deleted successfully' };
  }

  @Post('bulk')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.SOLUTION,
  })
  async bulkAction(
    @Body() bulkDto: BulkSolutionActionDto,
    @CurrentAdmin() admin: User,
  ) {
    const { ids, action } = bulkDto;
    const results: { id: string; success: boolean; error?: string }[] = [];

    for (const id of ids) {
      try {
        switch (action) {
          case 'delete':
            await this.prisma.solution.update({
              where: { id: id },
              data: {
                is_deleted: true,
                deleted_at: new Date(),
                deleted_by: admin.id,
              },
            });
            break;
          case 'unflag':
            await this.prisma.solution.update({
              where: { id: id },
              data: {
                is_flagged: false,
                flagged_at: null,
                flagged_reason: null,
              },
            });
            break;
          case 'publish':
            await this.prisma.solution.update({
              where: { id: id },
              data: {
                is_published: true,
                published_at: new Date(),
                published_by: admin.id,
              },
            });
            break;
          case 'unpublish':
            await this.prisma.solution.update({
              where: { id: id },
              data: { is_published: false },
            });
            break;
        }
        results.push({ id, success: true });
      } catch (_error) {
        results.push({ id, success: false, error: 'Failed to perform action' });
      }
    }

    await this.auditService.log({
      performerId: admin.id,
      action: `BULK_${action.toUpperCase()}_SOLUTIONS`,
      entityType: 'SOLUTION',
      entityId: ids.join(','),
      newValues: { action, count: ids.length },
    });

    return { results };
  }
}
