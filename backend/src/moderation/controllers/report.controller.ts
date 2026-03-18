import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  Query,
  UseGuards,
  Request,
} from '@nestjs/common';
import { JwtAuthGuard } from '../../auth/guards/jwt-auth.guard';
import { RolesGuard } from '../../auth/guards/roles.guard';
import { RequireRoles } from '../../admin/decorators/roles.decorator';
import { UserRole } from '@prisma/client';
import { ReportService } from '../services/report.service';
import {
  CreateReportDto,
  QueryReportsDto,
} from '../../common/dto/moderation.dto';

@Controller('reports')
@UseGuards(JwtAuthGuard)
export class ReportController {
  constructor(private reportService: ReportService) {}

  @Post()
  async create(
    @Request() req: { user: { id: string } },
    @Body() dto: CreateReportDto,
  ) {
    return this.reportService.create(req.user.id, dto);
  }

  @Get()
  @UseGuards(RolesGuard)
  @RequireRoles(UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN)
  async findAll(@Query() query: QueryReportsDto) {
    return this.reportService.findAll(query);
  }

  @Get('entity/:entityType/:entityId')
  @UseGuards(RolesGuard)
  @RequireRoles(UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN)
  async getReportsByEntity(
    @Param('entityType') entityType: string,
    @Param('entityId') entityId: string,
  ) {
    return this.reportService.getReportsByEntity(
      entityType as
        | 'forum_post'
        | 'forum_comment'
        | 'solution'
        | 'solution_comment'
        | 'problem',
      entityId,
    );
  }

  @Get(':id')
  @UseGuards(RolesGuard)
  @RequireRoles(UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN)
  async findOne(@Param('id') id: string) {
    return this.reportService.findOne(id);
  }
}
