import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from './user.entity';
import { PrismaService } from '../prisma.service';

@Injectable()
export class UserService {
  constructor(
    @InjectRepository(User)
    private usersRepository: Repository<User>,
    private prisma: PrismaService,
  ) {}

  findAll(): Promise<User[]> {
    return this.usersRepository.find();
  }

  async getProfileWithRank(
    id: string,
  ): Promise<(User & { rank: number | null }) | null> {
    const user = await this.usersRepository.findOneBy({ id });
    if (!user) return null;

    const rankRecord = await this.prisma.globalRanking.findUnique({
      where: { user_id: id },
    });

    return {
      ...user,
      rank: rankRecord?.global_rank ?? null,
    };
  }

  findOne(id: string): Promise<User | null> {
    return this.usersRepository.findOneBy({ id });
  }

  async remove(id: string): Promise<void> {
    await this.usersRepository.delete(id);
  }

  findByUsername(username: string): Promise<User | null> {
    return this.usersRepository.findOneBy({ username });
  }

  findByEmail(email: string): Promise<User | null> {
    return this.usersRepository.findOneBy({ email });
  }

  async create(userData: Partial<User>): Promise<User> {
    const user = this.usersRepository.create(userData);
    return this.usersRepository.save(user);
  }

  async update(id: string, userData: Partial<User>): Promise<User> {
    await this.usersRepository.update(id, userData);
    const updatedUser = await this.findOne(id);
    if (!updatedUser) {
      throw new Error(`User with ID ${id} not found after update`);
    }
    return updatedUser;
  }

  async getUserStats(userId: string) {
    // 1. Get solved problems count grouped by difficulty
    const solvedSubmissions = await this.prisma.submission.findMany({
      where: {
        user_id: userId,
        status: 'Accepted',
      },
      distinct: ['problem_id'], // Count unique problems
      select: {
        problem: {
          select: {
            difficulty: true,
          },
        },
        created_at: true,
      },
    });

    const stats = {
      Easy: { count: 0, total: 0 },
      Medium: { count: 0, total: 0 },
      Hard: { count: 0, total: 0 },
    };

    solvedSubmissions.forEach((sub) => {
      const diff = sub.problem.difficulty;
      if (stats[diff]) {
        stats[diff].count++;
      }
    });

    // Get total counts per difficulty
    const problemCounts = await this.prisma.problem.groupBy({
      by: ['difficulty'],
      _count: {
        id: true,
      },
    });

    problemCounts.forEach((group) => {
      if (stats[group.difficulty]) {
        stats[group.difficulty].total = group._count.id;
      }
    });

    // 2. Calculate Streak
    // Get all accepted submissions dates, unique per day
    const distinctDates = new Set(
      solvedSubmissions.map((s) => s.created_at.toISOString().split('T')[0]),
    );

    let currentStreak = 0;
    const today = new Date().toISOString().split('T')[0];
    const yesterdayDate = new Date();
    yesterdayDate.setDate(yesterdayDate.getDate() - 1);
    const yesterday = yesterdayDate.toISOString().split('T')[0];

    // Check if user solved something today or yesterday to maintain streak
    if (distinctDates.has(today) || distinctDates.has(yesterday)) {
      let streak = 0;
      const checkDate = new Date();
      // Start checking from today if solved, else yesterday
      if (!distinctDates.has(today)) {
        checkDate.setDate(checkDate.getDate() - 1);
      }

      while (true) {
        const dateStr = checkDate.toISOString().split('T')[0];
        if (distinctDates.has(dateStr)) {
          streak++;
          checkDate.setDate(checkDate.getDate() - 1);
        } else {
          break;
        }
      }
      currentStreak = streak;
    }

    // 3. Activity Heatmap Data (Last 365 days)
    const oneYearAgo = new Date();
    oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);

    // Map to daily counts
    const dailyActivity = new Map<string, number>();

    const activityRaw = await this.prisma.submission.findMany({
      where: {
        user_id: userId,
        created_at: { gte: oneYearAgo },
      },
      select: { created_at: true },
    });

    activityRaw.forEach((sub) => {
      const date = sub.created_at.toISOString().split('T')[0];
      dailyActivity.set(date, (dailyActivity.get(date) || 0) + 1);
    });

    const heatmapData: { date: string; level: number }[] = [];
    // Convert map to array format expected by frontend
    dailyActivity.forEach((count, date) => {
      // Simple level logic: 0=0, 1-2=1, 3-5=2, 6-9=3, 10+=4
      let level = 0;
      if (count > 0) level = 1;
      if (count > 2) level = 2;
      if (count > 5) level = 3;
      if (count > 9) level = 4;

      heatmapData.push({ date, level });
    });

    return {
      stats,
      streak: currentStreak,
      totalSolved: solvedSubmissions.length,
      heatmap: heatmapData,
    };
  }
}
