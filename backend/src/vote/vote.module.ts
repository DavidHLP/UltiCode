import { Module } from '@nestjs/common';
import { VoteService } from './vote.service';
import { VoteController } from './vote.controller';
import { PrismaService } from '../prisma.service';

@Module({
  controllers: [VoteController],
  providers: [VoteService, PrismaService],
  exports: [VoteService],
})
export class VoteModule {}
