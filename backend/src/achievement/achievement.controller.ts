import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  UseGuards,
  Req,
} from '@nestjs/common';
import { AchievementService } from './achievement.service';
import {
  CreateAchievementDto,
  UpdateAchievementDto,
  AchievementQueryDto,
} from './achievement.dto';
import { AuthGuard } from '../auth/auth.guard';

interface AuthenticatedRequest {
  user: { id: string };
}

@Controller('admin/achievements')
@UseGuards(AuthGuard)
export class AdminAchievementController {
  constructor(private readonly achievementService: AchievementService) {}

  @Post()
  create(@Body() dto: CreateAchievementDto) {
    return this.achievementService.create(dto);
  }

  @Get()
  findAll(@Query() query: AchievementQueryDto) {
    return this.achievementService.findAll(query);
  }

  @Get(':id')
  findOne(@Param('id') id: string) {
    return this.achievementService.findOne(id);
  }

  @Put(':id')
  update(@Param('id') id: string, @Body() dto: UpdateAchievementDto) {
    return this.achievementService.update(id, dto);
  }

  @Delete(':id')
  remove(@Param('id') id: string) {
    return this.achievementService.remove(id);
  }

  @Post('seed')
  seedDefaults() {
    return this.achievementService.seedDefaultAchievements();
  }
}

@Controller('achievements')
@UseGuards(AuthGuard)
export class AchievementController {
  constructor(private readonly achievementService: AchievementService) {}

  @Get()
  findAll(@Query() query: AchievementQueryDto) {
    return this.achievementService.findAll(query);
  }

  @Get('my')
  getMyAchievements(@Req() req: AuthenticatedRequest) {
    return this.achievementService.getUserAchievements(req.user.id);
  }

  @Get('points')
  getMyPoints(@Req() req: AuthenticatedRequest) {
    return this.achievementService.getUserPoints(req.user.id);
  }

  @Get(':id')
  findOne(@Param('id') id: string) {
    return this.achievementService.findOne(id);
  }
}
