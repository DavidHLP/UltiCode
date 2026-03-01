import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  Query,
  UseGuards,
} from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { RequireRoles } from '../decorators/roles.decorator';
import { UserRole } from '../../user/user.service';
import { PermissionAction, PermissionResource } from '@prisma/client';
import { AdminSubmissionService } from '../services/admin-submission.service';
import {
  AdminSubmissionQueryDto,
  RejudgeSubmissionDto,
  BatchRejudgeDto,
} from '../dto/admin-submission.dto';

@Controller('admin/submissions')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard, CsrfGuard)
export class AdminSubmissionController {
  constructor(private readonly submissionService: AdminSubmissionService) {}

  @Get()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  async findAll(@Query() query: AdminSubmissionQueryDto) {
    return this.submissionService.findAll(query);
  }

  @Get('statistics')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  async getStatistics() {
    return this.submissionService.getStatistics();
  }

  @Get('statuses')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  async getStatuses() {
    return this.submissionService.getStatuses();
  }

  @Get('languages')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  async getLanguages() {
    return this.submissionService.getLanguages();
  }

  @Get(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  async findOne(@Param('id') id: string) {
    return this.submissionService.findOne(id);
  }

  @Post(':id/rejudge')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.PROBLEM,
  })
  async rejudge(@Param('id') id: string, @Body() dto: RejudgeSubmissionDto) {
    return this.submissionService.rejudge(id, dto.notifyUser);
  }

  @Post('batch-rejudge')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.PROBLEM,
  })
  async batchRejudge(@Body() dto: BatchRejudgeDto) {
    return this.submissionService.batchRejudge(dto);
  }
}
