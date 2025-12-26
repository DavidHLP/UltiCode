import { Module } from '@nestjs/common';
import { EdgeOperationsController } from './edge-operations.controller';
import { EdgeOperationsService } from './edge-operations.service';
import { PrismaService } from '../prisma.service';
import { VoteService } from '../vote/vote.service';

@Module({
  controllers: [EdgeOperationsController],
  providers: [EdgeOperationsService, PrismaService, VoteService],
})
export class EdgeOperationsModule {}
