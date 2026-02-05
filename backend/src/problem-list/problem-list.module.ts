import { Module } from '@nestjs/common';
import { ProblemListService } from './problem-list.service';
import { ProblemListController } from './problem-list.controller';
import { PrismaService } from '../prisma.service';
import { SubmissionModule } from '../submission/submission.module';
import { BookmarkModule } from '../bookmark/bookmark.module';
import { I18nModule } from '../i18n/i18n.module';
import { ProblemListStatsService } from './services/problem-list-stats.service';
import { ProblemListCrudService } from './services/problem-list-crud.service';
import { ProblemListRelationService } from './services/problem-list-relation.service';
import { ProblemListBookmarkService } from './services/problem-list-bookmark.service';
import { ProblemListCategoryService } from './services/problem-list-category.service';

@Module({
  imports: [SubmissionModule, BookmarkModule, I18nModule],
  providers: [
    PrismaService,
    // 子服务 - 按依赖顺序注册
    ProblemListStatsService,
    ProblemListCrudService,
    ProblemListRelationService,
    ProblemListBookmarkService,
    ProblemListCategoryService,
    // 主服务 - 依赖所有子服务
    ProblemListService,
  ],
  controllers: [ProblemListController],
  exports: [
    ProblemListService,
    ProblemListCrudService,
    ProblemListRelationService,
    ProblemListStatsService,
  ],
})
export class ProblemListModule {}
