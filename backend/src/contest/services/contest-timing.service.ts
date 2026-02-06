import { Injectable } from '@nestjs/common';
import { ContestStatus } from '@prisma/client';
import { PrismaService } from '../../prisma.service';
import { I18nService } from '../../i18n/i18n.service';
import {
  SupportedLocale,
  TRANSLATABLE_ENTITIES,
} from '../../i18n/i18n.constants';

@Injectable()
export class ContestTimingService {
  constructor(
    private prisma: PrismaService,
    private readonly i18nService: I18nService,
  ) {}

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

  async applyContestTranslations<T extends { id: string }>(
    contests: T[],
    locale: SupportedLocale,
  ): Promise<T[]> {
    if (contests.length === 0) return contests;

    const ids = contests.map((c) => c.id);
    const translationsMap = await this.i18nService.getBatchTranslations(
      'CONTEST',
      ids,
      locale,
    );

    return contests.map((contest) => {
      const translations: Map<string, string> =
        translationsMap.get(contest.id) ?? new Map<string, string>();
      return this.i18nService.applyTranslations(
        contest,
        translations,
        TRANSLATABLE_ENTITIES.CONTEST.fields,
      );
    });
  }
}
