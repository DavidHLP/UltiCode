import {
  Controller,
  Get,
  Post,
  Patch,
  Delete,
  Body,
  Param,
  Query,
  UseGuards,
} from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { RequireRoles } from '../decorators/roles.decorator';
import { CurrentAdmin } from '../decorators/current-admin.decorator';
import { UserRole, User } from '../../user/user.entity';
import { PermissionAction, PermissionResource } from '@prisma/client';
import { AdminTagService } from '../services/admin-tag.service';
import {
  TagQueryDto,
  CreateTagDto,
  UpdateTagDto,
  MergeTagDto,
  TagType,
} from '../dto/tag.dto';

@Controller('admin/tags')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard)
export class AdminTagController {
  constructor(private readonly tagService: AdminTagService) {}

  @Get()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.TAG,
  })
  async findAll(@Query() query: TagQueryDto): Promise<{
    data: any[];
    total: number;
    page: number;
    limit: number;
    totalPages: number;
  }> {
    return await this.tagService.findAll(query);
  }

  @Get(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.TAG,
  })
  async findOne(
    @Param('id') id: string,
    @Query('type') type: TagType = TagType.PROBLEM,
  ) {
    return this.tagService.findOne(id, type);
  }

  @Post()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.CREATE,
    resource: PermissionResource.TAG,
  })
  async create(
    @Body() createTagDto: CreateTagDto,
    @CurrentAdmin() admin: User,
  ) {
    return this.tagService.create(createTagDto, admin.id);
  }

  @Patch(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.TAG,
  })
  async update(
    @Param('id') id: string,
    @Body() updateTagDto: UpdateTagDto & { type: TagType }, // Ensure type is passed for context
    @CurrentAdmin() admin: User,
  ) {
    // We assume the type is passed in the body or known.
    // In a RESTful generic endpoint, ID collision between ProblemTag and ForumTag is possible (uuid),
    // but unlikely to collide. However, to be safe, we need 'type'.
    // Or we could have /admin/tags/problem/:id and /admin/tags/forum/:id.
    // But here we use a query param or body param.
    // Let's require type in the body for update as well, or as a query param.
    // For simplicity, let's assume it's in the body for now as UpdateTagDto doesn't mandate it but we need it.
    // Actually, let's look at the DTO. UpdateTagDto doesn't have type.
    // We should probably enforce passing type in Query for specific operations if ID isn't unique enough or for clarity.

    return this.tagService.update(
      id,
      updateTagDto,
      updateTagDto.type || TagType.PROBLEM,
      admin.id,
    );
  }

  @Delete(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.DELETE,
    resource: PermissionResource.TAG,
  })
  async remove(
    @Param('id') id: string,
    @Query('type') type: TagType = TagType.PROBLEM,
    @CurrentAdmin() admin: User,
  ) {
    return this.tagService.delete(id, type, admin.id);
  }

  @Post('merge')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE, // Merge is effectively an update/cleanup
    resource: PermissionResource.TAG,
  })
  async merge(
    @Body() mergeTagDto: MergeTagDto & { sourceId: string },
    @CurrentAdmin() admin: User,
  ) {
    return this.tagService.merge(
      mergeTagDto.sourceId,
      mergeTagDto.targetTagId,
      mergeTagDto.type,
      admin.id,
    );
  }
}
