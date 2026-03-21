import { PrismaClient } from '@prisma/client';
import { clearUsers, seedUsers } from './seed-users';
import { clearProblems, seedProblems } from './seed-problems';
import { clearContests, seedContests } from './seed-contests';
import { clearForum, seedForum } from './seed-forum';
import { clearProblemLists, seedProblemLists } from './seed-problem-lists';
import { clearSolutions, seedSolutions } from './seed-solutions';
import { clearSubmissions, seedSubmissions } from './seed-submissions';
import { clearEdgeOperations } from './seed-edge-operations';
import {
  clearSubmissionStatuses,
  seedSubmissionStatuses,
} from './seed-submission-statuses';
import { clearTranslations, seedTranslations } from './seed-translations';
import {
  clearPermissions,
  seedRolePermissions,
  seedAdminUsers,
} from './seed-permissions';

// New seed system imports
import { createSeedRunner } from './core/seed-runner';
import {
  createUsersSeeder,
  createSubmissionStatusesSeeder,
  createProblemTagsSeeder,
  createProblemsSeeder,
  createForumSeeder,
  createContestsSeeder,
  createSolutionsSeeder,
  createProblemListsSeeder,
  createSubmissionsSeeder,
  createTranslationsSeeder,
  createPermissionsSeeder,
  createFlaggedProblemsSeeder,
  createNotificationsSeeder,
  createRecommendationSeeder,
  createModerationQueueSeeder,
} from './modules';

/**
 * Feature flag to enable the new optimized seed system.
 * Set USE_NEW_SEED=true to use the new system.
 */
const USE_NEW_SEED = process.env.USE_NEW_SEED === 'true';

const prisma = new PrismaClient();

/**
 * 按外键依赖顺序清空所有表（先子表再父表）
 */
async function clearAll(): Promise<void> {
  console.log('🗑️  Clearing all data...');

  // 最内层/子表先清
  await clearTranslations(prisma);
  await clearEdgeOperations(prisma);
  await clearSubmissions(prisma);
  await clearSubmissionStatuses(prisma);
  await clearSolutions(prisma);
  await clearProblemLists(prisma);
  await clearForum(prisma);
  await clearContests(prisma);
  await clearProblems(prisma);
  await clearPermissions(prisma);
  await clearUsers(prisma);

  console.log('✅ All data cleared');
}

/**
 * 按依赖顺序初始化所有数据
 */
async function seedAll(): Promise<void> {
  console.log('🌱 Seeding data...');

  // 1. 基础/无依赖表
  const users = await seedUsers(prisma);
  console.log(`  ✓ Users: ${users.length} records`);

  // 1.5. Admin users with roles
  const adminUsers = await seedAdminUsers(prisma);
  console.log(`  ✓ Admin Users: ${adminUsers.count} records`);

  // 2. 主实体表
  const problems = await seedProblems(prisma);
  console.log(`  ✓ Problems: ${problems.count} records`);

  // 3. 依赖主实体的表
  const contests = await seedContests(prisma, { problems });
  console.log(`  ✓ Contests: ${contests.count} records`);

  const forum = await seedForum(prisma);
  console.log(
    `  ✓ Forum: ${forum.postsCount} posts, ${forum.commentsCount} comments`,
  );

  const problemLists = await seedProblemLists(prisma);
  console.log(`  ✓ Problem Lists: ${problemLists.count} records`);

  const solutions = await seedSolutions(prisma);
  console.log(
    `  ✓ Solutions: ${solutions.solutionsCount} solutions, ${solutions.commentsCount} comments`,
  );

  const statuses = await seedSubmissionStatuses(prisma);
  console.log(`  ✓ Submission Statuses: ${statuses.count} records`);

  const submissions = await seedSubmissions(prisma);
  console.log(`  ✓ Submissions: ${submissions.count} records`);

  // 4. I18n translations (depends on problems, contests, etc.)
  const translations = await seedTranslations(prisma);
  console.log(`  ✓ Translations: ${translations.count} records`);

  // 5. Role permissions (last, independent)
  const permissions = await seedRolePermissions(prisma);
  console.log(`  ✓ Role Permissions: ${permissions.count} records`);

  console.log('✅ All data seeded');
}

async function main(): Promise<void> {
  try {
    if (USE_NEW_SEED) {
      // New optimized seed system
      console.log('🚀 Using new optimized seed system...\n');

      const runner = createSeedRunner(
        prisma,
        process.env.SEED_VERBOSE === 'true',
      );

      // Register all seeders in dependency order
      runner.registerSeeders([
        // L0 - No dependencies
        createSubmissionStatusesSeeder,
        createProblemTagsSeeder,
        // L1 - Base entities
        createUsersSeeder,
        // L2 - Depends on L1
        createProblemsSeeder,
        createForumSeeder,
        // L3 - Depends on L2
        createContestsSeeder,
        createSolutionsSeeder,
        createProblemListsSeeder,
        createFlaggedProblemsSeeder,
        createModerationQueueSeeder,
        // L4 - Depends on L3
        createSubmissionsSeeder,
        // L4.5 - Recommendation data (depends on Problems, Users)
        createRecommendationSeeder,
        // L5 - Final layer
        createTranslationsSeeder,
        createPermissionsSeeder,
        createNotificationsSeeder,
      ]);

      const result = await runner.run({
        clear: true,
        verbose: process.env.SEED_VERBOSE === 'true',
      });

      if (!result.success) {
        console.error('❌ Seed failed:', result.error);
        process.exit(1);
      }
    } else {
      // Legacy seed system
      await clearAll();
      await seedAll();
    }
  } catch (error) {
    console.error('❌ Seed failed:', error);
    process.exit(1);
  } finally {
    await prisma.$disconnect();
  }
}

void main();
