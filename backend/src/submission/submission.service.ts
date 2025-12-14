import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { Submission, Prisma } from '@prisma/client';

@Injectable()
export class SubmissionService {
  constructor(private prisma: PrismaService) {}

  async findAll(
    problemId: number,
    userId?: string,
    skip: number = 0,
    take: number = 10,
  ): Promise<Submission[]> {
    const whereCondition: Prisma.SubmissionWhereInput = {
      problem_id: problemId,
    };

    if (userId) {
      whereCondition.user_id = userId;
    }

    const submissions = await this.prisma.submission.findMany({
      where: whereCondition,
      include: {
        user: {
          select: {
            id: true,
            username: true,
            avatar: true,
          },
        },
      },
      orderBy: {
        created_at: 'desc',
      },
      skip,
      take,
    });
    return submissions;
  }

  async findBest(
    problemId: number,
    userId: string,
  ): Promise<Submission | null> {
    const submission = await this.prisma.submission.findFirst({
      where: {
        problem_id: problemId,
        user_id: userId,
        status: 'Accepted',
      },
      orderBy: [{ runtime: 'asc' }, { memory: 'asc' }, { created_at: 'desc' }],
      include: {
        user: {
          select: {
            id: true,
            username: true,
            avatar: true,
          },
        },
      },
      take: 1,
    });

    return submission;
  }

  async findOne(id: string): Promise<Submission> {
    const submission = await this.prisma.submission.findUnique({
      where: { id },
      include: {
        user: {
          select: {
            id: true,
            username: true,
            avatar: true,
          },
        },
      },
    });

    if (!submission) {
      throw new NotFoundException(`Submission with ID ${id} not found`);
    }

    return submission;
  }
}
