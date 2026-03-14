import {
  Controller,
  Get,
  Param,
  Query,
  UseGuards,
  NotFoundException,
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiBearerAuth, ApiQuery } from '@nestjs/swagger';
import { AntiCheatService } from './anticheat.service';
import { JwtAuthGuard } from '../../auth/guards/jwt-auth.guard';
import { RolesGuard } from '../../auth/guards/roles.guard';
import { Roles } from '../../auth/decorators/roles.decorator';
import { UserRole } from '@prisma/client';

@ApiTags('Admin / Anti-Cheat')
@ApiBearerAuth()
@Controller('admin/anticheat/contest')
@UseGuards(JwtAuthGuard, RolesGuard)
@Roles(UserRole.ADMIN)
export class AntiCheatController {
  constructor(private readonly antiCheatService: AntiCheatService) {}

  @Get(':contestId/report')
  @ApiOperation({ summary: 'Get full anti-cheat report for a contest' })
  async getReport(@Param('contestId') contestId: string) {
    const report = await this.antiCheatService.generateReport(contestId);

    if (!report) {
      throw new NotFoundException(`Contest with ID ${contestId} not found`);
    }

    return report;
  }

  @Get(':contestId/similarity')
  @ApiOperation({ summary: 'Get similarity report for contest submissions' })
  @ApiQuery({
    name: 'threshold',
    required: false,
    type: Number,
    description: 'Similarity threshold (0-1), default 0.8',
  })
  async getSimilarityReport(
    @Param('contestId') contestId: string,
    @Query('threshold') threshold?: string,
  ) {
    const parsedThreshold = threshold ? parseFloat(threshold) : 0.8;

    if (isNaN(parsedThreshold) || parsedThreshold < 0 || parsedThreshold > 1) {
      return {
        error: 'Invalid threshold. Must be a number between 0 and 1.',
        similarity_pairs: [],
      };
    }

    const similarityPairs = await this.antiCheatService.detectSimilarity(
      contestId,
      parsedThreshold,
    );

    return {
      contest_id: contestId,
      threshold: parsedThreshold,
      total_pairs: similarityPairs.length,
      similarity_pairs: similarityPairs,
    };
  }

  @Get(':contestId/time-anomalies')
  @ApiOperation({ summary: 'Get time anomaly report for contest submissions' })
  @ApiQuery({
    name: 'minTime',
    required: false,
    type: Number,
    description: 'Minimum time in seconds for first submission, default 60',
  })
  async getTimeAnomalyReport(
    @Param('contestId') contestId: string,
    @Query('minTime') minTime?: string,
  ) {
    const parsedMinTime = minTime ? parseInt(minTime, 10) : undefined;

    if (parsedMinTime !== undefined && (isNaN(parsedMinTime) || parsedMinTime < 0)) {
      return {
        error: 'Invalid minTime. Must be a positive number.',
        time_anomalies: [],
      };
    }

    const timeAnomalies = await this.antiCheatService.checkTimeAnomaly(contestId, {
      minTime: parsedMinTime,
    });

    return {
      contest_id: contestId,
      min_time: parsedMinTime ?? 60,
      total_anomalies: timeAnomalies.length,
      time_anomalies: timeAnomalies,
    };
  }
}
