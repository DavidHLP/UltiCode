import {
  Controller,
  Get,
  Post,
  Param,
  Query,
  Body,
  UseGuards,
} from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { CurrentAdmin } from '../decorators/current-admin.decorator';
import { PermissionAction, PermissionResource } from '@prisma/client';
import { ProblemVersionService } from '../services/problem-version.service';
import {
  VersionQueryDto,
  RollbackVersionDto,
} from '../dto/problem-version.dto';
import type { User } from '../../user/user.service';

@Controller('admin/problems/:problemId/versions')
@UseGuards(AuthGuard, CsrfGuard, PermissionsGuard, RolesGuard)
@RequirePermissions({
  action: PermissionAction.READ,
  resource: PermissionResource.PROBLEM,
})
export class AdminProblemVersionController {
  constructor(private readonly versionService: ProblemVersionService) {}

  @Get()
  async getVersionHistory(
    @Param('problemId') problemId: string,
    @Query() query: VersionQueryDto,
  ) {
    const { limit = 20, page = 1 } = query;
    const offset = (page - 1) * limit;

    const result = await this.versionService.getVersionHistory(
      BigInt(problemId),
      { limit, offset },
    );

    return {
      versions: result.versions,
      pagination: {
        total: result.total,
        page,
        limit,
        totalPages: Math.ceil(result.total / limit),
      },
    };
  }

  @Get(':versionId')
  async getVersion(
    @Param('problemId') problemId: string,
    @Param('versionId') versionId: string,
  ) {
    return this.versionService.getVersion(BigInt(problemId), versionId);
  }

  @Get(':fromVersionId/diff/:toVersionId')
  async getVersionDiff(
    @Param('problemId') problemId: string,
    @Param('fromVersionId') fromVersionId: string,
    @Param('toVersionId') toVersionId: string,
  ) {
    return this.versionService.getVersionDiff(
      BigInt(problemId),
      fromVersionId,
      toVersionId,
    );
  }

  @Post(':versionId/rollback')
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.PROBLEM,
  })
  async rollbackToVersion(
    @Param('problemId') problemId: string,
    @Param('versionId') versionId: string,
    @Body() body: RollbackVersionDto,
    @CurrentAdmin() admin: User,
  ) {
    await this.versionService.rollbackToVersion(
      BigInt(problemId),
      versionId,
      admin.id,
    );

    return {
      success: true,
      message: `Successfully rolled back to version ${versionId}`,
    };
  }

  @Post('create-initial')
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.PROBLEM,
  })
  async createInitialVersion(
    @Param('problemId') problemId: string,
    @CurrentAdmin() admin: User,
  ) {
    const created = await this.versionService.createInitialVersion(
      BigInt(problemId),
      admin.id,
    );

    return {
      success: created,
      message: created
        ? 'Initial version snapshot created successfully'
        : 'Problem already has version history',
    };
  }
}
