/**
 * Seed system announcements only - for admin notifications page
 * Run with: npx ts-node prisma/seed-system-announcements.ts
 */
import { PrismaClient, NotificationType } from '@prisma/client';

const prisma = new PrismaClient();

const now = new Date();
const hoursAgo = (h: number) => new Date(now.getTime() - h * 60 * 60 * 1000);
const daysAgo = (d: number) => new Date(now.getTime() - d * 24 * 60 * 60 * 1000);

async function getFirstUserId(): Promise<string> {
  const user = await prisma.user.findFirst({
    select: { id: true },
  });
  if (!user) {
    throw new Error('No users found. Please seed users first.');
  }
  return user.id;
}

async function main() {
  console.log('🌱 Seeding system announcements...');

  // Get an admin user ID as the creator
  const creatorId = await getFirstUserId();
  console.log(`Using creator ID: ${creatorId}`);

  // Clear existing announcements
  await prisma.systemAnnouncement.deleteMany();
  console.log('  Cleared existing announcements');

  // Create system announcements
  const announcements = [
    {
      id: 'ann-001',
      title: 'Platform Maintenance Scheduled',
      content:
        'UltiCode will undergo scheduled maintenance on March 15, 2026 from 02:00 to 04:00 UTC. During this time, the platform will be temporarily unavailable. We apologize for any inconvenience.',
      type: NotificationType.SYSTEM,
      created_by: creatorId,
      created_at: hoursAgo(2),
    },
    {
      id: 'ann-002',
      title: 'Weekly Contest 423 Registration Open',
      content:
        'Weekly Contest 423 is now open for registration! The contest begins on Saturday at 10:30 AM UTC. Join thousands of programmers worldwide and test your problem-solving skills.',
      type: NotificationType.CONTEST,
      created_by: creatorId,
      created_at: hoursAgo(5),
    },
    {
      id: 'ann-003',
      title: 'New Feature: AI-Powered Code Review',
      content:
        'We are excited to introduce AI code review! Get instant feedback on your submissions including optimization suggestions, best practices, and potential bugs. This feature is now available for all premium users.',
      type: NotificationType.SYSTEM,
      created_by: creatorId,
      created_at: daysAgo(1),
    },
    {
      id: 'ann-004',
      title: 'Biweekly Contest 128 Results',
      content:
        'Congratulations to all participants of Biweekly Contest 128! The contest saw 8,542 submissions from 3,215 participants. Check out the leaderboard and editorial for detailed solutions.',
      type: NotificationType.CONTEST,
      created_by: creatorId,
      created_at: daysAgo(2),
    },
    {
      id: 'ann-005',
      title: 'New Problems Added: Dynamic Programming Series',
      content:
        'We have added 15 new problems focusing on dynamic programming techniques. Topics include: 1D/2D DP, Knapsack variants, LCS/LIS, Tree DP, and Digit DP. Start practicing now!',
      type: NotificationType.SYSTEM,
      created_by: creatorId,
      created_at: daysAgo(3),
    },
    {
      id: 'ann-006',
      title: 'Server Performance Upgrade Complete',
      content:
        'We have completed our server infrastructure upgrade. You should notice significantly faster page load times and submission processing. Average response time is now under 50ms.',
      type: NotificationType.SYSTEM,
      created_by: creatorId,
      created_at: daysAgo(5),
    },
    {
      id: 'ann-007',
      title: 'Holiday Special Contest Announcement',
      content:
        'Join us for the Spring Festival Special Contest on March 20th! This 3-hour contest will feature unique themed problems and exclusive badges for top performers. Registration opens next week.',
      type: NotificationType.CONTEST,
      created_by: creatorId,
      created_at: daysAgo(7),
    },
    {
      id: 'ann-008',
      title: 'Community Guidelines Update',
      content:
        'We have updated our community guidelines to ensure a positive experience for all users. Key changes include: improved code of conduct, clearer submission rules, and enhanced moderation policies.',
      type: NotificationType.SYSTEM,
      created_by: creatorId,
      created_at: daysAgo(10),
    },
  ];

  // Insert announcements
  for (const announcement of announcements) {
    await prisma.systemAnnouncement.create({
      data: announcement,
    });
  }

  console.log(`✅ Seeded ${announcements.length} system announcements`);

  // Verify
  const count = await prisma.systemAnnouncement.count();
  console.log(`Total announcements: ${count}`);
}

main()
  .catch((e) => {
    console.error('❌ Seed failed:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
