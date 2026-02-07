import { Injectable } from '@nestjs/common';
import { ContestStatus } from '@prisma/client';
import { PrismaService } from '../../prisma.service';

@Injectable()
export class ContestTimingService {
  constructor(private prisma: PrismaService) {}

  withTimingFields<
    T extends {
      start_time: Date;
      duration_minutes: number;
      status: ContestStatus;
    },
  >(contest: T) {
    const endTime = new Date(
      contest.start_time.getTime() + contest.duration_minutes * 60 * 1000,
    );
    const now = Date.now();
    const startsInSeconds = Math.max(
      0,
      Math.floor((contest.start_time.getTime() - now) / 1000),
    );
    const endsInSeconds = Math.max(
      0,
      Math.floor((endTime.getTime() - now) / 1000),
    );

    return {
      ...contest,
      end_time: endTime,
      starts_in_seconds: startsInSeconds,
      ends_in_seconds: endsInSeconds,
      can_register: contest.status === 'upcoming',
      can_start: contest.status === 'running',
    };
  }
}
