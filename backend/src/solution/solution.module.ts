import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { SolutionService } from './solution.service';
import { SolutionController } from './solution.controller';
import { SolutionMeta } from './solution-meta.entity';
import { SolutionAuthor } from './solution-author.entity';
import { SolutionBadge } from './solution-badge.entity';
import { SolutionTopic } from './solution-topic.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      SolutionMeta,
      SolutionAuthor,
      SolutionBadge,
      SolutionTopic,
    ]),
  ],
  providers: [SolutionService],
  controllers: [SolutionController],
  exports: [SolutionService],
})
export class SolutionModule {}
