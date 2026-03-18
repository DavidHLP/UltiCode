import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  Query,
  UseGuards,
  Request,
  Patch,
} from '@nestjs/common';
import { JwtAuthGuard } from '../../auth/guards/jwt-auth.guard';
import { RolesGuard } from '../../auth/guards/roles.guard';
import { RequireRoles } from '../../admin/decorators/roles.decorator';
import { UserRole } from '@prisma/client';
import { ModerationQueueService } from '../services/queue.service';
import {
  QueryModerationQueueDto,
  AssignModerationDto,
  PerformModerationActionDto,
  BatchModerationActionDto,
} from '../../common/dto/moderation.dto';

@Controller('moderation/queue')
@UseGuards(JwtAuthGuard, RolesGuard)
@RequireRoles(UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN)
export class ModerationQueueController {
  constructor(private queueService: ModerationQueueService) {}

  @Get()
  async findAll(@Query() query: QueryModerationQueueDto) {
    return this.queueService.findAll(query);
  }

  @Get('stats')
  async getStats() {
    return this.queueService.getStats();
  }

  @Get(':id')
  async findOne(@Param('id') id: string) {
    return this.queueService.findOne(id);
  }

  @Get('entity/:entityType/:entityId')
  async findByEntity(
    @Param('entityType') entityType: string,
    @Param('entityId') entityId: string,
  ) {
    return this.queueService.findByEntity(
      entityType as
        | 'forum_post'
        | 'forum_comment'
        | 'solution'
        | 'solution_comment'
        | 'problem',
      entityId,
    );
  }

  @Post(':id/claim')
  async claim(
    @Param('id') id: string,
    @Request() req: { user: { id: string } },
  ) {
    return this.queueService.claim(id, req.user.id);
  }

  @Post(':id/assign')
  async assign(
    @Param('id') id: string,
    @Body() dto: AssignModerationDto,
    @Request() req: { user: { id: string } },
  ) {
    return this.queueService.assign(id, dto, req.user.id);
  }

  @Patch(':id/unassign')
  async unassign(
    @Param('id') id: string,
    @Request() req: { user: { id: string } },
  ) {
    return this.queueService.unassign(id, req.user.id);
  }

  @Post(':id/action')
  async performAction(
    @Param('id') id: string,
    @Body() dto: PerformModerationActionDto,
    @Request() req: { user: { id: string } },
  ) {
    return this.queueService.performAction(
      id,
      dto.action,
      req.user.id,
      dto.note,
      dto.duration_days,
    );
  }

  @Post('batch-action')
  async batchAction(
    @Body() dto: BatchModerationActionDto,
    @Request() req: { user: { id: string } },
  ) {
    return this.queueService.batchAction(dto, req.user.id);
  }
}
