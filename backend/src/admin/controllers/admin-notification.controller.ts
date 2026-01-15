import {
  Controller,
  Post,
  Get,
  Delete,
  Body,
  Param,
  UseGuards,
} from '@nestjs/common';
import { AdminNotificationService } from '../services/admin-notification.service';
import { CreateNotificationDto } from '../dto/notification.dto';
import { CurrentAdmin } from '../decorators/current-admin.decorator';
import { User } from '../../user/user.entity';
import { PermissionAction, PermissionResource } from '@prisma/client';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';

@Controller('admin/notifications')
@UseGuards(AuthGuard, PermissionsGuard, CsrfGuard)
export class AdminNotificationController {
  constructor(private readonly notificationService: AdminNotificationService) {}

  @Post()
  @RequirePermissions({
    action: PermissionAction.CREATE,
    resource: PermissionResource.SYSTEM,
  })
  async create(@CurrentAdmin() user: User, @Body() dto: CreateNotificationDto) {
    return this.notificationService.create(user.id, dto);
  }

  @Get()
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.SYSTEM,
  })
  async findAll() {
    return this.notificationService.findAll();
  }

  @Delete(':id')
  @RequirePermissions({
    action: PermissionAction.DELETE,
    resource: PermissionResource.SYSTEM,
  })
  async delete(@Param('id') id: string) {
    return this.notificationService.delete(id);
  }
}
