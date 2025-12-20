import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';

@Injectable()
export class ProblemNoteService {
  constructor(private prisma: PrismaService) {}

  async save(userId: string, problemId: number, content: string) {
    return await this.prisma.problemNote.upsert({
      where: {
        user_id_problem_id: {
          user_id: userId,
          problem_id: problemId,
        },
      },
      update: { content },
      create: {
        user_id: userId,
        problem_id: problemId,
        content,
      },
    });
  }

  async findByProblem(userId: string, problemId: number) {
    return await this.prisma.problemNote.findUnique({
      where: {
        user_id_problem_id: {
          user_id: userId,
          problem_id: problemId,
        },
      },
    });
  }
}
