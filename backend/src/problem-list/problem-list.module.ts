import { Module } from '@nestjs/common';
import { ProblemListService } from './problem-list.service';
import { ProblemListController } from './problem-list.controller';
import { PrismaService } from '../prisma.service';
import { SubmissionModule } from '../submission/submission.module';
import { BookmarkModule } from '../bookmark/bookmark.module';
import { I18nModule } from '../i18n/i18n.module';

@Module({
  imports: [SubmissionModule, BookmarkModule, I18nModule],
  providers: [ProblemListService, PrismaService],
  controllers: [ProblemListController],
  exports: [ProblemListService],
})
export class ProblemListModule {}
