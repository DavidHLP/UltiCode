import {
  Injectable,
  NotFoundException,
  BadRequestException,
} from '@nestjs/common';
import { v4 as uuid } from 'uuid';
import { PrismaService } from '../../prisma.service';
import { RankingService } from '../ranking.service';

@Injectable()
export class ContestVirtualService {
  constructor(
    private prisma: PrismaService,
    private readonly rankingService: RankingService,
  ) {}

  async startVirtualContest(contestId: string, userId: string) {
    const contest = await this.prisma.contest.findUnique({
      where: { id: contestId },
    });

    if (!contest) {
      throw new NotFoundException('Contest not found');
    }

    if (contest.status !== 'finished') {
      throw new BadRequestException(
        'Can only start virtual contest for finished contests',
      );
    }

    const existingSession = await this.prisma.virtualContestSession.findFirst({
      where: {
        contest_id: contestId,
        user_id: userId,
        status: 'IN_PROGRESS',
      },
      orderBy: { started_at: 'desc' },
    });

    if (existingSession) {
      return existingSession;
    }

    const now = new Date();
    const endsAt = new Date(
      now.getTime() + contest.duration_minutes * 60 * 1000,
    );

    const sessionId: string = uuid();
    const session = await this.prisma.virtualContestSession.create({
      data: {
        id: sessionId,
        contest_id: contestId,
        user_id: userId,
        status: 'IN_PROGRESS',
        started_at: now,
        ends_at: endsAt,
      },
    });

    const virtualParticipantId: string = uuid();
    await this.prisma.contestParticipant.create({
      data: {
        id: virtualParticipantId,
        contest_id: contestId,
        user_id: userId,
        status: 'STARTED',
        started_at: now,
        is_virtual: true,
        virtual_session_id: session.id,
      },
    });

    return session;
  }

  async getVirtualSession(contestId: string, userId: string) {
    return this.prisma.virtualContestSession.findFirst({
      where: {
        contest_id: contestId,
        user_id: userId,
      },
      orderBy: { started_at: 'desc' },
    });
  }

  async finishVirtualContest(sessionId: string, userId: string): Promise<void> {
    const session = await this.prisma.virtualContestSession.findUnique({
      where: { id: sessionId },
    });

    if (!session || session.user_id !== userId) {
      throw new NotFoundException('Virtual session not found');
    }

    if (session.status !== 'IN_PROGRESS') {
      throw new BadRequestException('Virtual session is not in progress');
    }

    await this.prisma.$transaction(async (tx) => {
      await tx.virtualContestSession.update({
        where: { id: sessionId },
        data: {
          status: 'COMPLETED',
          finished_at: new Date(),
        },
      });

      await tx.contestParticipant.updateMany({
        where: { virtual_session_id: sessionId },
        data: {
          status: 'FINISHED',
          finished_at: new Date(),
        },
      });
    });

    const participant = await this.prisma.contestParticipant.findFirst({
      where: { virtual_session_id: sessionId },
    });

    if (participant) {
      await this.rankingService.finalizeVirtualRanking(participant.id);
    }
  }
}
