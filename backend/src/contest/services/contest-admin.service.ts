import { Injectable, NotFoundException } from '@nestjs/common';
import { ContestStatus } from '@prisma/client';
import { v4 as uuid } from 'uuid';
import { PrismaService } from '../../prisma.service';
import { CreateContestDto, UpdateContestDto } from '../dto';

@Injectable()
export class ContestAdminService {
  constructor(private prisma: PrismaService) {}

  async createContest(dto: CreateContestDto, userId: string) {
    const contestId: string = uuid();
    const contest = await this.prisma.contest.create({
      data: {
        id: contestId,
        title: dto.title,
        slug: dto.slug,
        contest_type: dto.contest_type,
        start_time: new Date(dto.start_time),
        duration_minutes: dto.duration_minutes,
        status: 'upcoming',
        ...(dto.penalty_per_wrong !== undefined && {
          penalty_per_wrong: dto.penalty_per_wrong,
        }),
        ...(dto.scoring_mode !== undefined && {
          scoring_mode: dto.scoring_mode,
        }),
        ...(dto.tie_breaker !== undefined && { tie_breaker: dto.tie_breaker }),
        is_rated: dto.is_rated,
        description: dto.description,
        cover_image: dto.cover_image,
        rules: dto.rules,
        created_by: userId,
      },
    });

    if (dto.problems && dto.problems.length > 0) {
      await this.prisma.contestProblem.createMany({
        data: dto.problems.map((p) => {
          const problemId: string = uuid();
          return {
            id: problemId,
            contest_id: contest.id,
            problem_id: BigInt(p.problem_id),
            problem_index: p.problem_index,
            score: p.score,
            ...(p.penalty_per_wrong !== undefined && {
              penalty_per_wrong: p.penalty_per_wrong,
            }),
          };
        }),
      });
    }

    return contest;
  }

  async updateContest(id: string, dto: UpdateContestDto) {
    const contest = await this.prisma.contest.findUnique({
      where: { id },
    });

    if (!contest) {
      throw new NotFoundException('Contest not found');
    }

    return this.prisma.contest.update({
      where: { id },
      data: {
        ...(dto.title && { title: dto.title }),
        ...(dto.slug && { slug: dto.slug }),
        ...(dto.contest_type && { contest_type: dto.contest_type }),
        ...(dto.start_time && { start_time: new Date(dto.start_time) }),
        ...(dto.duration_minutes !== undefined && {
          duration_minutes: dto.duration_minutes,
        }),
        ...(dto.penalty_per_wrong !== undefined && {
          penalty_per_wrong: dto.penalty_per_wrong,
        }),
        ...(dto.scoring_mode !== undefined && {
          scoring_mode: dto.scoring_mode,
        }),
        ...(dto.tie_breaker !== undefined && { tie_breaker: dto.tie_breaker }),
        ...(dto.is_rated !== undefined && { is_rated: dto.is_rated }),
        ...(dto.description !== undefined && { description: dto.description }),
        ...(dto.cover_image !== undefined && { cover_image: dto.cover_image }),
        ...(dto.rules !== undefined && { rules: dto.rules }),
        ...(dto.is_visible !== undefined && { is_visible: dto.is_visible }),
      },
    });
  }

  async deleteContest(id: string): Promise<void> {
    const contest = await this.prisma.contest.findUnique({
      where: { id },
    });

    if (!contest) {
      throw new NotFoundException('Contest not found');
    }

    await this.prisma.contest.delete({ where: { id } });
  }

  async updateContestStatus(id: string, status: ContestStatus) {
    return this.prisma.contest.update({
      where: { id },
      data: { status },
    });
  }
}
