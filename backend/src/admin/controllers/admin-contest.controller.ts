/* eslint-disable @typescript-eslint/no-unsafe-assignment */
import {
  Controller,
  Get,
  Post,
  Patch,
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
import { PrismaService } from '../../prisma.service';
import type { User } from '../../user/user.service';
import { UserRole } from '../../user/user.service';
import { PermissionAction, PermissionResource, Prisma } from '@prisma/client';
import {
  CreateContestDto,
  UpdateContestDto,
  ContestProblemDto,
  ContestQueryDto,
} from '../dto/contest.dto';

@Controller('admin/contests')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard, CsrfGuard)
export class AdminContestController {
  constructor(
    private prisma: PrismaService,
    private auditService: AuditService,
  ) {}

  @Get()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.CONTEST,
  })
  async findAll(@Query() query: ContestQueryDto) {
    const {
      search,
      type,
      status,
      page = 1,
      limit = 20,
      sortBy = 'start_time',
      sortOrder = 'desc',
    } = query;

    const skip = (page - 1) * limit;

    const where: Prisma.ContestWhereInput = {};

    if (search) {
      where.OR = [
        { title: { contains: search } },
        { slug: { contains: search } },
      ];
    }

    if (type) {
      where.contest_type = type as any;
    }

    if (status) {
      where.status = status.toUpperCase() as any;
    }

    const [contests, total] = await Promise.all([
      this.prisma.contest.findMany({
        where,
        skip,
        take: limit,
        orderBy: { [sortBy]: sortOrder },
        include: {
          _count: {
            select: {
              participants: true,
              problems: true,
            },
          },
        },
      }),
      this.prisma.contest.count({ where }),
    ]);

    return {
      data: contests.map((c) => ({
        ...c,
        id: c.id.toString(),
        participant_count: c._count.participants,
        problem_count: c._count.problems,
        _count: undefined,
      })),
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
  }

  @Get(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.CONTEST,
  })
  async findOne(@Param('id') id: string) {
    const contest = await this.prisma.contest.findUnique({
      where: { id: id },
      include: {
        participants: {
          include: {
            user: {
              select: {
                id: true,
                username: true,
                name: true,
              },
            },
          },
        },
        problems: {
          include: {
            problem: {
              select: {
                id: true,
                slug: true,
                title: true,
                difficulty: true,
              },
            },
          },
          orderBy: { problem_index: 'asc' },
        },
      },
    });

    if (!contest) {
      return null;
    }

    return {
      ...contest,
      id: contest.id.toString(),
      participants: contest.participants.map((p) => ({
        ...p,
        id: p.id.toString(),
        contest_id: p.contest_id.toString(),
        user_id: p.user_id.toString(),
      })),
      problems: contest.problems.map((p) => ({
        ...p,
        id: p.id.toString(),
        contest_id: p.contest_id.toString(),
        problem_id: p.problem_id.toString(),
      })),
    };
  }

  @Post()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.CREATE,
    resource: PermissionResource.CONTEST,
  })
  async create(
    @Body() createContestDto: CreateContestDto,
    @CurrentAdmin() admin: User,
  ) {
    const {
      slug,
      title,
      description,
      type,
      start_time,
      duration,
      is_published,
      problem_ids,
    } = createContestDto;

    const startTime = new Date(start_time);
    const id = Date.now().toString();

    const contest = await this.prisma.contest.create({
      data: {
        id,
        slug,
        title,
        description,
        contest_type: type as any,
        start_time: startTime,
        duration_minutes: duration,
        status: 'upcoming',
        is_visible: is_published || false,
      },
    });

    // Add problems if provided
    if (problem_ids && problem_ids.length > 0) {
      for (let i = 0; i < problem_ids.length; i++) {
        try {
          await this.prisma.contestProblem.create({
            data: {
              id: crypto.randomUUID(),
              contest_id: id,
              problem_id: BigInt(problem_ids[i]),
              problem_index: String.fromCharCode(65 + i), // A, B, C...
              score: 100,
            },
          });
        } catch (_error) {
          // Skip if problem doesn't exist
        }
      }
    }

    await this.auditService.log({
      performerId: admin.id,
      action: 'CREATE_CONTEST',
      entityType: 'CONTEST',
      entityId: contest.id.toString(),
      newValues: { slug, title, type },
    });

    return {
      ...contest,
      id: contest.id.toString(),
    };
  }

  @Patch(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.CONTEST,
  })
  async update(
    @Param('id') id: string,
    @Body() updateContestDto: UpdateContestDto,
    @CurrentAdmin() admin: User,
  ) {
    const oldContest = await this.prisma.contest.findUnique({
      where: { id: id },
    });

    if (!oldContest) {
      return null;
    }

    const {
      slug,
      title,
      description,
      type,
      start_time,
      duration,
      is_published,
    } = updateContestDto;

    const updateData: Prisma.ContestUpdateInput = {};
    if (slug !== undefined) updateData.slug = slug;
    if (title !== undefined) updateData.title = title;
    if (description !== undefined) updateData.description = description;
    if (type !== undefined) updateData.contest_type = type as any;
    if (start_time !== undefined) updateData.start_time = new Date(start_time);
    if (duration !== undefined) updateData.duration_minutes = duration;
    if (is_published !== undefined) updateData.is_visible = is_published;

    const contest = await this.prisma.contest.update({
      where: { id: id },
      data: updateData,
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'UPDATE_CONTEST',
      entityType: 'CONTEST',
      entityId: id,
      oldValues: {
        slug: oldContest.slug,
        title: oldContest.title,
      },
      newValues: updateContestDto,
    });

    return {
      ...contest,
      id: contest.id.toString(),
    };
  }

  @Delete(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.DELETE,
    resource: PermissionResource.CONTEST,
  })
  async remove(@Param('id') id: string, @CurrentAdmin() admin: User) {
    const oldContest = await this.prisma.contest.findUnique({
      where: { id: id },
    });

    await this.prisma.contest.delete({
      where: { id: id },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'DELETE_CONTEST',
      entityType: 'CONTEST',
      entityId: id,
      oldValues: {
        slug: oldContest?.slug,
        title: oldContest?.title,
      },
    });

    return { message: 'Contest deleted successfully' };
  }

  @Post(':id/problems')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.CONTEST,
  })
  async addProblem(
    @Param('id') id: string,
    @Body() problemDto: ContestProblemDto,
    @CurrentAdmin() admin: User,
  ) {
    const contest = await this.prisma.contest.findUnique({
      where: { id: id },
      include: {
        problems: {
          orderBy: { problem_index: 'desc' },
          take: 1,
        },
      },
    });

    if (!contest) {
      return null;
    }

    // Simple auto-increment for index: A, B, C...
    const lastIndex = contest.problems[0]?.problem_index || '@'; // '@' is before 'A'
    const nextIndex = String.fromCharCode(lastIndex.charCodeAt(0) + 1);

    const contestProblem = await this.prisma.contestProblem.create({
      data: {
        id: crypto.randomUUID(),
        contest_id: id,
        problem_id: BigInt(problemDto.problem_id),
        problem_index: nextIndex,
        score: problemDto.score || 100,
      },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'ADD_CONTEST_PROBLEM',
      entityType: 'CONTEST',
      entityId: id,
      newValues: { problem_id: problemDto.problem_id },
    });

    return {
      ...contestProblem,
      id: contestProblem.id.toString(),
      contest_id: contestProblem.contest_id.toString(),
      problem_id: contestProblem.problem_id.toString(),
    };
  }

  @Delete(':id/problems/:problemId')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.CONTEST,
  })
  async removeProblem(
    @Param('id') id: string,
    @Param('problemId') problemId: string,
  ) {
    await this.prisma.contestProblem.deleteMany({
      where: {
        contest_id: id,
        problem_id: BigInt(problemId),
      },
    });

    return { message: 'Problem removed from contest successfully' };
  }

  @Get(':id/rankings')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.CONTEST,
  })
  async getRankings(@Param('id') id: string) {
    const rankings = await this.prisma.contestRanking.findMany({
      where: { contest_id: id },
      orderBy: [{ total_score: 'desc' }, { total_penalty: 'asc' }],
      include: {
        user: {
          select: {
            id: true,
            username: true,
            name: true,
          },
        },
      },
    });

    return {
      data: rankings.map((r) => ({
        ...r,
        id: r.id.toString(),
        contest_id: r.contest_id.toString(),
        user_id: r.user_id.toString(),
      })),
    };
  }

  @Post(':id/start')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.CONTEST,
  })
  async startContest(@Param('id') id: string, @CurrentAdmin() admin: User) {
    const contest = await this.prisma.contest.update({
      where: { id: id },
      data: {
        is_visible: true,
        start_time: new Date(),
        status: 'running',
      },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'START_CONTEST',
      entityType: 'CONTEST',
      entityId: id,
      newValues: { started: true },
    });

    return {
      ...contest,
      id: contest.id.toString(),
    };
  }

  @Post(':id/end')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.CONTEST,
  })
  async endContest(@Param('id') id: string, @CurrentAdmin() admin: User) {
    const contest = await this.prisma.contest.update({
      where: { id: id },
      data: {
        status: 'finished',
      },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'END_CONTEST',
      entityType: 'CONTEST',
      entityId: id,
      newValues: { ended: true },
    });

    return {
      ...contest,
      id: contest.id.toString(),
    };
  }
}
