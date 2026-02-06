import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import {
  GlobalRankingQueryDto,
  PaginatedResult,
  GlobalRankingEntry,
} from '../dto';

@Injectable()
export class GlobalRankingQueryService {
  constructor(private prisma: PrismaService) {}

  async getGlobalRanking(
    query: GlobalRankingQueryDto,
  ): Promise<PaginatedResult<GlobalRankingEntry>> {
    const page = Number(query.page || 1);
    const limit = Number(query.limit || 50);
    const { country } = query;
    const skip = (page - 1) * limit;

    const where = {
      ...(country ? { country } : {}),
    };

    const [rankings, total] = await Promise.all([
      this.prisma.globalRanking.findMany({
        where,
        skip,
        take: limit,
        orderBy: [{ global_rank: 'asc' }],
      }),
      this.prisma.globalRanking.count({ where }),
    ]);

    const items: GlobalRankingEntry[] = rankings.map((r) => ({
      rank: r.global_rank,
      userId: r.user_id,
      username: r.username,
      avatar: r.avatar,
      country: r.country,
      rating: r.rating,
      maxRating: r.max_rating,
      ratingTitle: r.rating_title,
      maxRatingTitle: r.max_rating_title,
      contestsAttended: r.contests_attended,
      badge: r.badge,
    }));

    return {
      items,
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
  }
}
