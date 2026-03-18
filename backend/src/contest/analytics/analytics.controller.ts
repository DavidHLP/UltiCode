import { Controller, Get, Param, Query, UseGuards, Req } from '@nestjs/common';
import { Request } from 'express';
import { AnalyticsService } from './analytics.service';
import { AuthGuard } from '../../auth/auth.guard';

interface RequestWithUser extends Request {
  user: {
    id: string;
    [key: string]: any;
  };
}

@Controller('contests')
export class AnalyticsController {
  constructor(private readonly analyticsService: AnalyticsService) {}

  /**
   * GET /contests/:contestId/analytics
   * Get contest analytics report
   * Query params:
   * - refresh: boolean - Force regenerate the report
   */
  @Get(':contestId/analytics')
  async getContestAnalytics(
    @Param('contestId') contestId: string,
    @Query('refresh') refresh?: string,
  ) {
    // If refresh is requested, generate new report
    if (refresh === 'true') {
      const report =
        await this.analyticsService.generateContestReport(contestId);
      return report;
    }

    // Otherwise, try to get stored report first
    const storedReport = await this.analyticsService.getStoredReport(contestId);

    // If no stored report exists, generate a new one
    if (!storedReport) {
      return this.analyticsService.generateContestReport(contestId);
    }

    return storedReport;
  }

  /**
   * GET /contests/user/history
   * Get current user's contest performance history
   * Query params:
   * - limit: number - Maximum number of entries (default: 20)
   */
  @Get('user/history')
  @UseGuards(AuthGuard)
  async getUserPerformanceHistory(
    @Query('limit') limit?: string,
    @Req() req?: RequestWithUser,
  ) {
    const parsedLimit = limit ? parseInt(limit, 10) : 20;
    return this.analyticsService.getUserPerformanceHistory(
      req!.user.id,
      parsedLimit,
    );
  }
}
