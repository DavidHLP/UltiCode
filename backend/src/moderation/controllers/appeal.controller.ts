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
import { AppealService } from '../services/appeal.service';
import {
  CreateAppealDto,
  QueryAppealsDto,
  ReviewAppealDto,
} from '../../common/dto/moderation.dto';

@Controller('appeals')
@UseGuards(JwtAuthGuard)
export class AppealController {
  constructor(private appealService: AppealService) {}

  @Post()
  async create(
    @Request() req: { user: { id: string } },
    @Body() dto: CreateAppealDto,
  ) {
    return this.appealService.create(req.user.id, dto);
  }

  @Get('my')
  async getMyAppeals(@Request() req: { user: { id: string } }) {
    return this.appealService.getAppealsByUser(req.user.id);
  }

  @Get()
  @UseGuards(RolesGuard)
  @RequireRoles(UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN)
  async findAll(@Query() query: QueryAppealsDto) {
    return this.appealService.findAll(query);
  }

  @Get('stats')
  @UseGuards(RolesGuard)
  @RequireRoles(UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN)
  async getStats() {
    return this.appealService.getStats();
  }

  @Get(':id')
  @UseGuards(RolesGuard)
  @RequireRoles(UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN)
  async findOne(@Param('id') id: string) {
    return this.appealService.findOne(id);
  }

  @Patch(':id/review')
  @UseGuards(RolesGuard)
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  async review(
    @Param('id') id: string,
    @Body() dto: ReviewAppealDto,
    @Request() req: { user: { id: string } },
  ) {
    return this.appealService.review(id, req.user.id, dto);
  }
}
