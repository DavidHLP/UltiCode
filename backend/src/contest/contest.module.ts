import { Module } from '@nestjs/common';
import { BullModule } from '@nestjs/bullmq';
import { ContestService } from './contest.service';
import { RankingService } from './ranking.service';
import { RatingService } from './rating.service';
import { ContestSchedulerService } from './contest-scheduler.service';
import { ContestProcessor } from './contest.processor';
import { ContestController, RankingController } from './contest.controller';
import { PrismaService } from '../prisma.service';
import { I18nModule } from '../i18n/i18n.module';
import { I18nService } from '../i18n/i18n.service';
import { NotificationModule } from '../notification/notification.module';
import { ScoringRuleService } from './services/scoring-rule.service';
import { ScoringService } from './scoring/scoring.service';
import { ScoringRuleController } from './admin/scoring-rule.controller';

// Ranking sub-services
import { RankingHelperService } from './services/ranking-helper.service';
import { GlobalRankingQueryService } from './services/global-ranking-query.service';
import { ContestRankingCalcService } from './services/contest-ranking-calc.service';
import { ContestRankingQueryService } from './services/contest-ranking-query.service';

// Contest sub-services
import { ContestTimingService } from './services/contest-timing.service';
import { ContestQueryService } from './services/contest-query.service';
import { ContestParticipationService } from './services/contest-participation.service';
import { ContestVirtualService } from './services/contest-virtual.service';
import { ContestAdminService } from './services/contest-admin.service';

@Module({
  imports: [
    BullModule.registerQueue({
      name: 'contest',
    }),
    I18nModule,
    NotificationModule,
  ],
  providers: [
    PrismaService,
    I18nService,
    // Contest sub-services - dependency order
    ContestTimingService,
    ContestAdminService,
    ContestParticipationService,
    ContestVirtualService,
    ContestQueryService,
    // Ranking sub-services - dependency order
    RankingHelperService,
    GlobalRankingQueryService,
    ContestRankingCalcService,
    ContestRankingQueryService,
    // Scoring services
    ScoringRuleService,
    ScoringService,
    // Main services
    ContestService,
    RankingService,
    RatingService,
    ContestSchedulerService,
    ContestProcessor,
  ],
  controllers: [ContestController, RankingController, ScoringRuleController],
  exports: [
    ContestService,
    RankingService,
    RatingService,
    ScoringRuleService,
    ScoringService,
  ],
})
export class ContestModule {}
