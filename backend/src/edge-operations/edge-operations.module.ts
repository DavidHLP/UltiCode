import { Module } from '@nestjs/common';
import { EdgeOperationsController } from './edge-operations.controller';
import { EdgeOperationsService } from './edge-operations.service';
import { PrismaService } from '../prisma.service';
import { VoteService } from '../vote/vote.service';
import { CollectionModule } from '../collection/collection.module';

@Module({
  imports: [CollectionModule],
  controllers: [EdgeOperationsController],
  providers: [EdgeOperationsService, PrismaService, VoteService],
})
export class EdgeOperationsModule {}
