import {
  Injectable,
  NotFoundException,
  BadRequestException,
} from '@nestjs/common';
import { ContestParticipantStatus } from '@prisma/client';
import { v4 as uuid } from 'uuid';
import { PrismaService } from '../../prisma.service';
import { ParticipationStatus } from '../dto';

@Injectable()
export class ContestParticipationService {
  constructor(private prisma: PrismaService) {}

  async registerForContest(contestId: string, userId: string): Promise<void> {
    await this.prisma.$transaction(async (tx) => {
      const contest = await tx.contest.findUnique({
        where: { id: contestId },
      });

      if (!contest) {
        throw new NotFoundException('Contest not found');
      }

      if (contest.status !== 'upcoming') {
        throw new BadRequestException(
          'Can only register for upcoming contests',
        );
      }

      // Use create with unique constraint to prevent duplicate registrations
      const participantId: string = uuid();
      try {
        await tx.contestParticipant.create({
          data: {
            id: participantId,
            contest_id: contestId,
            user_id: userId,
            status: 'REGISTERED',
            is_virtual: false,
          },
        });
      } catch (error) {
        // Handle unique constraint violation
        const prismaError = error as { code?: string };
        if (prismaError.code === 'P2002') {
          throw new BadRequestException('Already registered for this contest');
        }
        throw error;
      }

      await tx.contest.update({
        where: { id: contestId },
        data: { registered_count: { increment: 1 } },
      });
    });
  }

  async unregisterFromContest(
    contestId: string,
    userId: string,
  ): Promise<void> {
    const contest = await this.prisma.contest.findUnique({
      where: { id: contestId },
    });

    if (!contest) {
      throw new NotFoundException('Contest not found');
    }

    if (contest.status !== 'upcoming') {
      throw new BadRequestException(
        'Can only unregister from upcoming contests',
      );
    }

    const participant = await this.prisma.contestParticipant.findFirst({
      where: {
        contest_id: contestId,
        user_id: userId,
        is_virtual: false,
      },
    });

    if (!participant) {
      throw new BadRequestException('Not registered for this contest');
    }

    await this.prisma.contestParticipant.delete({
      where: { id: participant.id },
    });

    await this.prisma.contest.update({
      where: { id: contestId },
      data: { registered_count: { decrement: 1 } },
    });
  }

  async getParticipationStatus(
    contestId: string,
    userId: string,
  ): Promise<ParticipationStatus> {
    const participant = await this.prisma.contestParticipant.findFirst({
      where: {
        contest_id: contestId,
        user_id: userId,
        is_virtual: false,
      },
    });

    if (!participant) {
      return {
        isRegistered: false,
        status: null,
        participantId: null,
        virtualSessionId: null,
        startedAt: null,
        finishedAt: null,
        totalScore: 0,
        totalPenalty: 0,
      };
    }

    return {
      isRegistered: true,
      status: participant.status,
      participantId: participant.id,
      virtualSessionId: participant.virtual_session_id,
      startedAt: participant.started_at,
      finishedAt: participant.finished_at,
      totalScore: participant.total_score,
      totalPenalty: participant.total_penalty,
    };
  }

  async getUserContests(
    userId: string,
    type: 'registered' | 'participated' | 'virtual',
  ) {
    const statusMap: Record<string, ContestParticipantStatus[]> = {
      registered: ['REGISTERED'],
      participated: ['STARTED', 'FINISHED'],
      virtual: ['REGISTERED', 'STARTED', 'FINISHED'],
    };

    const participants = await this.prisma.contestParticipant.findMany({
      where: {
        user_id: userId,
        status: { in: statusMap[type] },
        is_virtual: type === 'virtual',
      },
      include: {
        contest: true,
      },
      orderBy: {
        contest: {
          start_time: 'desc',
        },
      },
    });

    return {
      participants,
      statusMap,
    };
  }
}
