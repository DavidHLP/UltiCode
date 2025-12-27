import {
  Controller,
  Get,
  Post,
  Delete,
  Param,
  Query,
  Body,
  UseGuards,
  Req,
} from '@nestjs/common';
import { Request } from 'express';
import { ContestService } from './contest.service';
import { RankingService } from './ranking.service';
import { RatingService } from './rating.service';
import { AuthGuard } from '../auth/auth.guard';
import {
  ContestQueryDto,
  ContestRankingQueryDto,
  GlobalRankingQueryDto,
} from './dto';
import type { ContestStats } from './contest.service';

interface RequestWithUser extends Request {
  user: {
    id: string;
    [key: string]: any;
  };
}

@Controller('contest')
export class ContestController {
  constructor(
    private readonly contestService: ContestService,
    private readonly rankingService: RankingService,
    private readonly ratingService: RatingService,
  ) {}

  // =========================================================================
  // CONTEST QUERIES
  // =========================================================================

  @Get('list')
  findAll(@Query() query: ContestQueryDto) {
    return this.contestService.findAll(query);
  }

  @Get('upcoming')
  findUpcoming() {
    return this.contestService.findUpcoming();
  }

  @Get('running')
  findRunning() {
    return this.contestService.findRunning();
  }

  @Get('past')
  findPast(
    @Query('page') page: number = 1,
    @Query('limit') limit: number = 10,
  ) {
    return this.contestService.findPast(Number(page), Number(limit));
  }

  @Get('stats')
  getStats(): Promise<ContestStats> {
    return this.contestService.getStats();
  }

  @Get(':id')
  findOne(@Param('id') id: string) {
    return this.contestService.findOne(id);
  }

  // =========================================================================
  // RANKINGS
  // =========================================================================

  @Get('global-ranking')
  getGlobalRanking() {
    return this.contestService.getGlobalRanking();
  }

  @Get(':id/ranking')
  getContestRanking(
    @Param('id') id: string,
    @Query() query: ContestRankingQueryDto,
  ) {
    return this.rankingService.getContestRanking(id, query);
  }

  @Get(':id/live-ranking')
  getLiveRanking(@Param('id') id: string, @Query('limit') limit: number = 100) {
    return this.rankingService.getLiveRanking(id, Number(limit));
  }

  // =========================================================================
  // PARTICIPATION (Authenticated)
  // =========================================================================

  @Post(':id/register')
  @UseGuards(AuthGuard)
  register(@Param('id') id: string, @Req() req: RequestWithUser) {
    return this.contestService.registerForContest(id, req.user.id);
  }

  @Delete(':id/register')
  @UseGuards(AuthGuard)
  unregister(@Param('id') id: string, @Req() req: RequestWithUser) {
    return this.contestService.unregisterFromContest(id, req.user.id);
  }

  @Get(':id/participation')
  @UseGuards(AuthGuard)
  getParticipation(@Param('id') id: string, @Req() req: RequestWithUser) {
    return this.contestService.getParticipationStatus(id, req.user.id);
  }

  // =========================================================================
  // VIRTUAL CONTEST (Authenticated)
  // =========================================================================

  @Post(':id/virtual/start')
  @UseGuards(AuthGuard)
  startVirtual(@Param('id') id: string, @Req() req: RequestWithUser) {
    return this.contestService.startVirtualContest(id, req.user.id);
  }

  @Get(':id/virtual/session')
  @UseGuards(AuthGuard)
  getVirtualSession(@Param('id') id: string, @Req() req: RequestWithUser) {
    return this.contestService.getVirtualSession(id, req.user.id);
  }

  @Post(':id/virtual/finish')
  @UseGuards(AuthGuard)
  finishVirtual(
    @Param('id') id: string,
    @Body('sessionId') sessionId: string,
    @Req() req: RequestWithUser,
  ) {
    return this.contestService.finishVirtualContest(sessionId, req.user.id);
  }

  // =========================================================================
  // USER CONTESTS (Authenticated)
  // =========================================================================

  @Get('user/my-contests')
  @UseGuards(AuthGuard)
  getMyContests(
    @Query('type')
    type: 'registered' | 'participated' | 'virtual' = 'participated',
    @Req() req: RequestWithUser,
  ) {
    return this.contestService.getUserContests(req.user.id, type);
  }

  @Get('user/history')
  @UseGuards(AuthGuard)
  getContestHistory(@Req() req: RequestWithUser) {
    return this.rankingService.getUserContestHistory(req.user.id);
  }

  @Get('user/rating-history')
  @UseGuards(AuthGuard)
  getRatingHistory(@Req() req: RequestWithUser) {
    return this.ratingService.getUserRatingHistory(req.user.id);
  }
}

// =========================================================================
// RANKINGS CONTROLLER (Separate for cleaner organization)
// =========================================================================

@Controller('rankings')
export class RankingController {
  constructor(
    private readonly rankingService: RankingService,
    private readonly ratingService: RatingService,
  ) {}

  @Get('global')
  getGlobalRanking(@Query() query: GlobalRankingQueryDto) {
    return this.rankingService.getGlobalRanking(query);
  }

  @Get('user/:userId')
  getUserContestHistory(@Param('userId') userId: string) {
    return this.rankingService.getUserContestHistory(userId);
  }

  @Get('user/:userId/rating-history')
  getUserRatingHistory(@Param('userId') userId: string) {
    return this.ratingService.getUserRatingHistory(userId);
  }
}
