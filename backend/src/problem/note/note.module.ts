import { Module } from '@nestjs/common';
import { ProblemNoteService } from './note.service';
import { ProblemNoteController } from './note.controller';
import { PrismaService } from '../../prisma.service';

@Module({
  controllers: [ProblemNoteController],
  providers: [ProblemNoteService, PrismaService],
  exports: [ProblemNoteService],
})
export class ProblemNoteModule {}
