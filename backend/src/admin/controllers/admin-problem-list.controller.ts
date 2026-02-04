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
  NotFoundException,
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
  CreateProblemListDto,
  UpdateProblemListDto,
  ProblemListQueryDto,
  UpdateProblemListProblemsDto,
} from '../dto/problem-list.dto';
import { v4 as uuidv4 } from 'uuid';

@Controller('admin/problem-lists')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard, CsrfGuard)
export class AdminProblemListController {
  constructor(
    private prisma: PrismaService,
    private auditService: AuditService,
  ) {}

  @Get()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM_LIST,
  })
  async findAll(@Query() query: ProblemListQueryDto) {
    const {
      search,
      is_featured,
      is_public,
      page = 1,
      limit = 20,
      sortBy = 'updated_at',
      sortOrder = 'desc',
    } = query;

    const skip = (page - 1) * limit;

    const where: Prisma.ProblemListWhereInput = {};

    if (search) {
      where.OR = [
        { name: { contains: search } },
        { description: { contains: search } },
      ];
    }

    if (is_featured !== undefined) {
      where.is_featured = is_featured;
    }

    if (is_public !== undefined) {
      where.is_public = is_public;
    }

    const lists = await this.prisma.problemList.findMany({
      where,
      skip,
      take: limit,
      orderBy: { [sortBy]: sortOrder },
      include: {
        _count: {
          select: {
            problemRelations: true,
          },
        },
      },
    });

    const total = await this.prisma.problemList.count({ where });

    return {
      data: lists.map((list) => ({
        ...list,
        problem_count: list._count.problemRelations,
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
    resource: PermissionResource.PROBLEM_LIST,
  })
  async findOne(@Param('id') id: string) {
    const list = await this.prisma.problemList.findUnique({
      where: { id },
      include: {
        problemRelations: {
          include: {
            problem: true,
          },
          orderBy: {
            sort_order: 'asc',
          },
        },
      },
    });

    if (!list) {
      throw new NotFoundException('Problem list not found');
    }

    return {
      ...list,
      problems: list.problemRelations.map((rel) => ({
        ...rel.problem,
        sort_order: rel.sort_order,
        added_at: rel.added_at,
      })),
    };
  }

  @Post()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.CREATE,
    resource: PermissionResource.PROBLEM_LIST,
  })
  async create(
    @Body() createDto: CreateProblemListDto,
    @CurrentAdmin() admin: User,
  ) {
    const id: string = uuidv4();
    const {
      name,
      description,
      is_public = true,
      is_featured = false,
      banner_tag,
      banner_icon,
      banner_theme,
      banner_order,
      author_id,
    } = createDto;

    const list = await this.prisma.problemList.create({
      data: {
        id,
        name,
        description,
        is_public,
        is_featured,
        banner_tag,
        banner_icon,
        banner_theme,
        banner_order,
        author_id: author_id || admin.id,
        created_at: new Date(),
        updated_at: new Date(),
      },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'CREATE_PROBLEM_LIST',
      entityType: 'PROBLEM_LIST',
      entityId: id,
      newValues: createDto,
    });

    return list;
  }

  @Patch(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.PROBLEM_LIST,
  })
  async update(
    @Param('id') id: string,
    @Body() updateDto: UpdateProblemListDto,
    @CurrentAdmin() admin: User,
  ) {
    const oldList = await this.prisma.problemList.findUnique({
      where: { id },
    });

    if (!oldList) {
      throw new NotFoundException('Problem list not found');
    }

    const list = await this.prisma.problemList.update({
      where: { id },
      data: {
        ...updateDto,
        updated_at: new Date(),
      },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'UPDATE_PROBLEM_LIST',
      entityType: 'PROBLEM_LIST',
      entityId: id,
      oldValues: oldList,
      newValues: updateDto,
    });

    return list;
  }

  @Delete(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.DELETE,
    resource: PermissionResource.PROBLEM_LIST,
  })
  async remove(@Param('id') id: string, @CurrentAdmin() admin: User) {
    const oldList = await this.prisma.problemList.findUnique({
      where: { id },
    });

    if (!oldList) {
      throw new NotFoundException('Problem list not found');
    }

    await this.prisma.problemList.delete({
      where: { id },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'DELETE_PROBLEM_LIST',
      entityType: 'PROBLEM_LIST',
      entityId: id,
      oldValues: oldList,
    });

    return { message: 'Problem list deleted successfully' };
  }

  @Post(':id/problems')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.PROBLEM_LIST,
  })
  async updateProblems(
    @Param('id') id: string,
    @Body() updateProblemsDto: UpdateProblemListProblemsDto,
    @CurrentAdmin() admin: User,
  ) {
    const list = await this.prisma.problemList.findUnique({
      where: { id },
    });

    if (!list) {
      throw new NotFoundException('Problem list not found');
    }

    // Use transaction to update problems
    await this.prisma.$transaction(async (tx) => {
      // Remove existing relations
      await tx.problemListProblemRelation.deleteMany({
        where: { list_id: id },
      });

      // Add new relations
      if (updateProblemsDto.problems.length > 0) {
        await tx.problemListProblemRelation.createMany({
          data: updateProblemsDto.problems.map((p) => ({
            list_id: id,
            problem_id: BigInt(p.problem_id),
            sort_order: p.sort_order,
          })),
        });
      }

      // Update list timestamp
      await tx.problemList.update({
        where: { id },
        data: { updated_at: new Date() },
      });
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'UPDATE_PROBLEM_LIST_CONTENT',
      entityType: 'PROBLEM_LIST',
      entityId: id,
      newValues: { problem_count: updateProblemsDto.problems.length },
    });

    return { message: 'Problem list content updated successfully' };
  }
}
