import {
  Controller,
  Get,
  Patch,
  Post,
  Delete,
  Param,
  Query,
  Body,
  UseGuards,
  Req,
} from '@nestjs/common';
import type { Request } from 'express';
import { NotificationService } from './notification.service';
import {
  NotificationQueryDto,
  UpdateNotificationDto,
  UpdateNotificationPreferencesDto,
} from './dto';
import { AuthGuard } from '../auth/auth.guard';

interface AuthenticatedRequest extends Request {
  user: { id: string };
}

@Controller('notifications')
@UseGuards(AuthGuard)
export class NotificationController {
  constructor(private readonly notificationService: NotificationService) {}

  @Get()
  list(@Req() req: AuthenticatedRequest, @Query() query: NotificationQueryDto) {
    return this.notificationService.list(req.user.id, query);
  }

  @Get('unread-count')
  getUnreadCount(@Req() req: AuthenticatedRequest) {
    return this.notificationService.getUnreadCount(req.user.id);
  }

  @Get('preferences')
  getPreferences(@Req() req: AuthenticatedRequest) {
    return this.notificationService.getPreferences(req.user.id);
  }

  @Patch('preferences')
  updatePreferences(
    @Req() req: AuthenticatedRequest,
    @Body() dto: UpdateNotificationPreferencesDto,
  ) {
    return this.notificationService.updatePreferences(req.user.id, dto);
  }

  @Post('mark-all-read')
  markAllRead(@Req() req: AuthenticatedRequest) {
    return this.notificationService.markAllRead(req.user.id);
  }

  @Delete('clear')
  clearAll(@Req() req: AuthenticatedRequest) {
    return this.notificationService.clearAll(req.user.id);
  }

  @Patch(':id')
  updateNotification(
    @Req() req: AuthenticatedRequest,
    @Param('id') id: string,
    @Body() dto: UpdateNotificationDto,
  ) {
    return this.notificationService.updateNotification(req.user.id, id, dto);
  }

  @Delete(':id')
  deleteNotification(
    @Req() req: AuthenticatedRequest,
    @Param('id') id: string,
  ) {
    return this.notificationService.deleteNotification(req.user.id, id);
  }
}
