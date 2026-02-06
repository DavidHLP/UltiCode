import {
  Injectable,
  BadRequestException,
  NotFoundException,
  ForbiddenException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { EdgeOperationTargetType } from '@prisma/client';
import { v4 as uuidv4 } from 'uuid';
import type { CreateSolutionDto } from '../dto/create-solution.dto';

@Injectable()
export class SolutionCrudService {
  constructor(private readonly prisma: PrismaService) {}

  async create(problemId: string, userId: string, dto: CreateSolutionDto) {
    const submission = await this.prisma.submission.findFirst({
      where: {
        problem_id: BigInt(problemId),
        user_id: userId,
        status: 'Accepted',
      },
    });

    if (!submission) {
      throw new BadRequestException(
        'You must have an accepted submission to create a solution',
      );
    }

    const existing = await this.prisma.solution.findFirst({
      where: {
        problem_id: BigInt(problemId),
        user_id: userId,
      },
      select: { id: true },
    });

    if (existing) {
      throw new BadRequestException(
        'Solution already exists. Please edit your existing solution.',
      );
    }

    const summary = this.buildSummary(dto.content);

    return this.prisma.$transaction(async (tx) => {
      const solutionId: string = uuidv4();
      const solution = await tx.solution.create({
        data: {
          id: solutionId,
          problem_id: BigInt(problemId),
          user_id: userId,
          title: dto.title,
          content: dto.content,
          summary,
          language: dto.language,
          tags: dto.tags ?? [],
        },
      });

      await tx.problem.update({
        where: { id: BigInt(problemId) },
        data: { has_solution: true },
      });

      return solution;
    });
  }

  async update(id: string, userId: string, dto: CreateSolutionDto) {
    const solution = await this.prisma.solution.findUnique({
      where: { id },
    });

    if (!solution) {
      throw new NotFoundException('Solution not found');
    }

    if (solution.user_id !== userId) {
      throw new ForbiddenException('You can only update your own solutions');
    }

    const summary = this.buildSummary(dto.content);

    return this.prisma.solution.update({
      where: { id },
      data: {
        title: dto.title,
        content: dto.content,
        summary,
        language: dto.language,
        tags: dto.tags ?? [],
      },
    });
  }

  async delete(id: string, userId: string) {
    const solution = await this.prisma.solution.findUnique({
      where: { id },
    });

    if (!solution) {
      throw new NotFoundException('Solution not found');
    }

    if (solution.user_id !== userId) {
      throw new ForbiddenException('You can only delete your own solutions');
    }

    const commentIds = await this.prisma.solutionComment.findMany({
      where: { solution_id: id },
      select: { id: true },
    });

    await this.prisma.$transaction(async (tx) => {
      if (commentIds.length) {
        await tx.edgeOperation.deleteMany({
          where: {
            target_type: EdgeOperationTargetType.SOLUTION_COMMENT,
            target_id: { in: commentIds.map((comment) => comment.id) },
          },
        });
      }

      await tx.edgeOperation.deleteMany({
        where: {
          target_type: EdgeOperationTargetType.SOLUTION,
          target_id: id,
        },
      });

      await tx.solution.delete({
        where: { id },
      });
    });

    const remaining = await this.prisma.solution.count({
      where: { problem_id: solution.problem_id },
    });

    if (remaining === 0) {
      await this.prisma.problem.update({
        where: { id: solution.problem_id },
        data: { has_solution: false },
      });
    }

    return { success: true };
  }

  private buildSummary(content: string): string {
    const MAX_SUMMARY_LENGTH = 180;
    const plain = content
      .replace(/```[\s\S]*?```/g, '')
      .replace(/`[^`]*`/g, '')
      .replace(/!\[[^\]]*]\([^)]+\)/g, '')
      .replace(/\[[^\]]*]\([^)]+\)/g, '')
      .replace(/[#>*_~`>-]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();

    if (!plain) {
      return '';
    }

    if (plain.length <= MAX_SUMMARY_LENGTH) {
      return plain;
    }

    return `${plain.slice(0, MAX_SUMMARY_LENGTH).trim()}...`;
  }
}
