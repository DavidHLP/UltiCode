import {
  Controller,
  Get,
  Query,
  UseGuards,
  Param,
  Post,
  Body,
  Delete,
  Patch,
} from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequireRoles } from '../decorators/roles.decorator';
import { UserRole } from '@prisma/client';
import { AdminCommentService } from '../services/admin-comment.service';
import {
  CommentQueryDto,
  CommentType,
  FlagCommentDto,
  BulkCommentActionDto,
} from '../dto/comment.dto';
import { CurrentAdmin } from '../decorators/current-admin.decorator';
import type { User } from '@prisma/client';

@Controller('admin/comments')
@UseGuards(AuthGuard, RolesGuard, CsrfGuard)
@RequireRoles(UserRole.ADMIN, UserRole.MODERATOR, UserRole.SUPER_ADMIN)
export class AdminCommentController {
  constructor(private readonly commentService: AdminCommentService) {}

  @Get()
  async findAll(@Query() query: CommentQueryDto) {
    return this.commentService.findAll(query);
  }

  @Post('bulk')
  async bulkAction(
    @Body() dto: BulkCommentActionDto,
    @CurrentAdmin() user: User,
  ): Promise<any[]> {
    // Basic bulk implementation - iterates for now
    const results: any[] = [];
    for (const id of dto.ids) {
      if (dto.action === 'delete') {
        results.push(
          await this.commentService.softDelete(id, dto.type, user.id),
        );
      } else if (dto.action === 'unflag') {
        results.push(await this.commentService.unflag(id, dto.type, user.id));
      }
    }
    return results;
  }

  @Patch(':type/:id/flag')
  async flag(
    @Param('type') type: CommentType,
    @Param('id') id: string,
    @Body() dto: FlagCommentDto,
    @CurrentAdmin() user: User,
  ): Promise<any> {
    return this.commentService.flag(id, type, dto.reason, user.id);
  }

  @Patch(':type/:id/unflag')
  async unflag(
    @Param('type') type: CommentType,
    @Param('id') id: string,
    @CurrentAdmin() user: User,
  ): Promise<any> {
    return this.commentService.unflag(id, type, user.id);
  }

  @Delete(':type/:id')
  async delete(
    @Param('type') type: CommentType,
    @Param('id') id: string,
    @CurrentAdmin() user: User,
  ): Promise<any> {
    return this.commentService.softDelete(id, type, user.id);
  }
}
