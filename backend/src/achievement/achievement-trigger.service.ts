import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { AchievementService, AchievementType } from './achievement.service';

@Injectable()
export class AchievementTriggerService {
  private readonly logger = new Logger(AchievementTriggerService.name);

  constructor(
    private prisma: PrismaService,
    private achievementService: AchievementService,
  ) {}

  /**
   * Called when a submission is accepted - triggers achievement checks for problem solving
   */
  async onSubmissionAccepted(
    userId: string,
    _problemId: bigint,
  ): Promise<void> {
    try {
      // Count unique problems solved by user
      const solvedProblems = await this.prisma.submission.groupBy({
        by: ['problem_id'],
        where: {
          user_id: userId,
          status: 'Accepted',
        },
      });

      const problemsSolvedCount = solvedProblems.length;

      // Count total submissions
      const totalSubmissions = await this.prisma.submission.count({
        where: { user_id: userId },
      });

      // Check and award problem-solving achievements
      await this.achievementService.checkAndAwardAchievements(
        userId,
        AchievementType.PROBLEMS_SOLVED,
        problemsSolvedCount,
      );

      // Check and award submission achievements
      await this.achievementService.checkAndAwardAchievements(
        userId,
        AchievementType.SUBMISSIONS_MADE,
        totalSubmissions,
      );

      this.logger.debug(
        `Achievement check completed for user ${userId}: ${problemsSolvedCount} problems solved, ${totalSubmissions} submissions`,
      );
    } catch (error) {
      this.logger.error(
        `Failed to check achievements for user ${userId}: ${error}`,
      );
    }
  }

  /**
   * Called when a user participates in a contest
   */
  async onContestParticipation(userId: string): Promise<void> {
    try {
      // Count unique contests participated
      const participations = await this.prisma.contestParticipant.count({
        where: { user_id: userId },
      });

      await this.achievementService.checkAndAwardAchievements(
        userId,
        AchievementType.CONTEST_PARTICIPATION,
        participations,
      );

      this.logger.debug(
        `Contest participation achievement check for user ${userId}: ${participations} contests`,
      );
    } catch (error) {
      this.logger.error(
        `Failed to check contest participation achievements for user ${userId}: ${error}`,
      );
    }
  }

  /**
   * Called when a user wins a contest (rank 1)
   */
  async onContestWin(userId: string): Promise<void> {
    try {
      // Count contest wins (final_rank = 1)
      const wins = await this.prisma.contestParticipant.count({
        where: {
          user_id: userId,
          final_rank: 1,
        },
      });

      await this.achievementService.checkAndAwardAchievements(
        userId,
        AchievementType.CONTEST_WINS,
        wins,
      );

      this.logger.debug(
        `Contest win achievement check for user ${userId}: ${wins} wins`,
      );
    } catch (error) {
      this.logger.error(
        `Failed to check contest win achievements for user ${userId}: ${error}`,
      );
    }
  }

  /**
   * Called when a user creates a forum post
   */
  async onForumPost(userId: string): Promise<void> {
    try {
      const posts = await this.prisma.forumPost.count({
        where: { user_id: userId },
      });

      await this.achievementService.checkAndAwardAchievements(
        userId,
        AchievementType.FORUM_POSTS,
        posts,
      );

      this.logger.debug(
        `Forum post achievement check for user ${userId}: ${posts} posts`,
      );
    } catch (error) {
      this.logger.error(
        `Failed to check forum post achievements for user ${userId}: ${error}`,
      );
    }
  }

  /**
   * Called when a user writes a solution
   */
  async onSolutionWritten(userId: string): Promise<void> {
    try {
      const solutions = await this.prisma.solution.count({
        where: { user_id: userId },
      });

      await this.achievementService.checkAndAwardAchievements(
        userId,
        AchievementType.SOLUTIONS_WRITTEN,
        solutions,
      );

      this.logger.debug(
        `Solution achievement check for user ${userId}: ${solutions} solutions`,
      );
    } catch (error) {
      this.logger.error(
        `Failed to check solution achievements for user ${userId}: ${error}`,
      );
    }
  }

  /**
   * Called daily to check streak achievements
   */
  async onDailyStreakCheck(): Promise<void> {
    try {
      // Get all users who have been active in the last 2 days
      const activeUsers = await this.prisma.user.findMany({
        where: {
          submissions: {
            some: {
              created_at: {
                gte: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000),
              },
            },
          },
        },
        select: { id: true },
      });

      for (const user of activeUsers) {
        await this.checkUserStreak(user.id);
      }

      this.logger.log(
        `Streak check completed for ${activeUsers.length} active users`,
      );
    } catch (error) {
      this.logger.error(`Failed to check streak achievements: ${error}`);
    }
  }

  /**
   * Check and update streak for a specific user
   */
  async checkUserStreak(userId: string): Promise<number> {
    // Get all unique days with submissions
    const submissions = await this.prisma.$queryRaw<{ date: Date }[]>`
      SELECT DISTINCT DATE(created_at) as date
      FROM submissions
      WHERE user_id = ${userId}
      ORDER BY date DESC
    `;

    if (submissions.length === 0) {
      return 0;
    }

    let streak = 0;
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    for (let i = 0; i < submissions.length; i++) {
      const submissionDate = new Date(submissions[i].date);
      submissionDate.setHours(0, 0, 0, 0);

      const expectedDate = new Date(today);
      expectedDate.setDate(expectedDate.getDate() - i);

      if (submissionDate.getTime() === expectedDate.getTime()) {
        streak++;
      } else {
        break;
      }
    }

    // Check streak achievements
    await this.achievementService.checkAndAwardAchievements(
      userId,
      AchievementType.STREAK_DAYS,
      streak,
    );

    return streak;
  }

  /**
   * Called when user's rating changes
   */
  async onRatingChange(userId: string, newRating: number): Promise<void> {
    try {
      await this.achievementService.checkAndAwardAchievements(
        userId,
        AchievementType.RATING_MILESTONE,
        newRating,
      );

      this.logger.debug(
        `Rating milestone check for user ${userId}: rating ${newRating}`,
      );
    } catch (error) {
      this.logger.error(
        `Failed to check rating achievements for user ${userId}: ${error}`,
      );
    }
  }
}
