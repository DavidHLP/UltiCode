import { PrismaClient } from '@prisma/client';
import { clearUsers, seedUsers } from './seed-users';
import { clearProblems, seedProblems } from './seed-problems';
import { clearContests, seedContests } from './seed-contests';
import { clearForum, seedForum } from './seed-forum';
import { clearProblemLists, seedProblemLists } from './seed-problem-lists';
import { clearSolutions, seedSolutions } from './seed-solutions';
import { clearSubmissions, seedSubmissions } from './seed-submissions';
import { clearVotes, seedVotes } from './seed-vote';
import { clearViews, seedViews } from './seed-view';

const prisma = new PrismaClient();

/**
 * 按外键依赖顺序清空所有表（先子表再父表）
 */
async function clearAll(): Promise<void> {
  console.log('🗑️  Clearing all data...');

  // 最内层/子表先清
  await clearViews(prisma);
  await clearVotes(prisma);
  await clearSubmissions(prisma);
  await clearSolutions(prisma);
  await clearProblemLists(prisma);
  await clearForum(prisma);
  await clearContests(prisma);
  await clearProblems(prisma);
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

  const votes = await seedVotes(prisma);
  console.log(`  ✓ Votes: ${votes.votesCount} records`);

  const views = await seedViews(prisma);
  console.log(`  ✓ Views: ${views.viewsCount} records`);

  const submissions = await seedSubmissions(prisma);
  console.log(`  ✓ Submissions: ${submissions.count} records`);

  console.log('✅ All data seeded');
}

async function main(): Promise<void> {
  try {
    await clearAll();
    await seedAll();
  } catch (error) {
    console.error('❌ Seed failed:', error);
    process.exit(1);
  } finally {
    await prisma.$disconnect();
  }
}

void main();
