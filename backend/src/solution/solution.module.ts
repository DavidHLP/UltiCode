import { Module } from '@nestjs/common';
import { SolutionService } from './solution.service';
import { SolutionController } from './solution.controller';
import { PrismaService } from '../prisma.service';

@Module({
  providers: [SolutionService, PrismaService],
  controllers: [SolutionController],
  exports: [SolutionService],
})
export class SolutionModule {}
