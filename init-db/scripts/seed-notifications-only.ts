/**
 * Seed notifications only - for adding notification data to existing database
 * Run with: npx ts-node prisma/seed-notifications-only.ts
 */
import { PrismaClient, NotificationType, NotificationCategory } from '@prisma/client';

const prisma = new PrismaClient();

const NOTIFICATION_IDS = {
  SYS_PLATFORM_UPDATE: 'notif-sys-001',
  SYS_WEEKLY_CONTEST: 'notif-sys-002',
  SYS_NEW_FEATURE: 'notif-sys-003',
  SUB_ACCEPTED_TWO_SUM: 'notif-sub-001',
  SUB_ACCEPTED_REVERSE_LL: 'notif-sub-002',
  SUB_WRONG_ANSWER: 'notif-sub-003',
  SUB_TLE: 'notif-sub-004',
  SUB_ACCEPTED_MEDIAN: 'notif-sub-005',
  CONTEST_STARTING: 'notif-contest-001',
  CONTEST_REGISTERED: 'notif-contest-002',
  CONTEST_RESULT: 'notif-contest-003',
  SOCIAL_COMMENT_REPLY: 'notif-soc-001',
  SOCIAL_MENTION: 'notif-soc-002',
  SOCIAL_FOLLOW: 'notif-soc-003',
  SOCIAL_UPVOTE: 'notif-soc-004',
} as const;

const now = new Date();
const hoursAgo = (h: number) => new Date(now.getTime() - h * 60 * 60 * 1000);
const daysAgo = (d: number) => new Date(now.getTime() - d * 24 * 60 * 60 * 1000);

async function getExistingUserIds(): Promise<string[]> {
  const users = await prisma.user.findMany({
    select: { id: true },
    take: 10,
  });
  return users.map((u) => u.id);
}

async function main() {
  console.log('🌱 Seeding notifications...');

  // Get existing user IDs
  const userIds = await getExistingUserIds();

  if (userIds.length === 0) {
    console.log('❌ No users found. Please seed users first.');
    return;
  }

  console.log(`Found ${userIds.length} users to assign notifications to.`);

  // Clear existing notifications first
  await prisma.notification.deleteMany();
  console.log('  Cleared existing notifications');

  // Create notifications using available users
  const notifications = [
    // System announcements
    {
      id: NOTIFICATION_IDS.SYS_PLATFORM_UPDATE,
      user_id: userIds[0],
      type: NotificationType.SYSTEM,
      category: NotificationCategory.SYSTEM,
      title: 'Platform Maintenance Scheduled',
      body: 'UltiCode will undergo scheduled maintenance on March 15, 2026 from 02:00 to 04:00 UTC. During this time, the platform will be temporarily unavailable.',
      link: '/announcements/maintenance-march-2026',
      metadata: { priority: 'high', maintenance_window: '2026-03-15T02:00:00Z' },
      is_read: false,
      read_at: null,
      created_at: hoursAgo(2),
    },
    {
      id: NOTIFICATION_IDS.SYS_WEEKLY_CONTEST,
      user_id: userIds[0],
      type: NotificationType.CONTEST,
      category: NotificationCategory.SYSTEM,
      title: 'Weekly Contest 423 Starting Soon',
      body: 'Weekly Contest 423 begins in 2 hours! Register now to compete with programmers worldwide.',
      link: '/contests/weekly-423',
      metadata: { contest_id: 'weekly-423', start_time: hoursAgo(0).toISOString() },
      is_read: false,
      read_at: null,
      created_at: hoursAgo(1),
    },
    {
      id: NOTIFICATION_IDS.SYS_NEW_FEATURE,
      user_id: userIds[1] || userIds[0],
      type: NotificationType.SYSTEM,
      category: NotificationCategory.SYSTEM,
      title: 'New Feature: AI-Powered Code Review',
      body: 'Introducing AI code review! Get instant feedback on your submissions including optimization suggestions and best practices.',
      link: '/features/ai-code-review',
      metadata: { feature: 'ai-code-review', version: '1.0.0' },
      is_read: true,
      read_at: hoursAgo(12),
      created_at: daysAgo(1),
    },
    // Submission notifications
    {
      id: NOTIFICATION_IDS.SUB_ACCEPTED_TWO_SUM,
      user_id: userIds[2] || userIds[0],
      type: NotificationType.SUBMISSION,
      category: NotificationCategory.SYSTEM,
      title: 'Submission Accepted! 🎉',
      body: 'Your solution to "Two Sum" has been accepted! Runtime: 52ms (faster than 95% of submissions). Memory: 42.1 MB.',
      link: '/submissions/sub-two-sum-001',
      metadata: {
        problem_slug: 'two-sum',
        status: 'ACCEPTED',
        runtime: 52,
        memory: 42100,
        percentile: 95,
      },
      is_read: true,
      read_at: hoursAgo(5),
      created_at: hoursAgo(6),
    },
    {
      id: NOTIFICATION_IDS.SUB_ACCEPTED_REVERSE_LL,
      user_id: userIds[3] || userIds[0],
      type: NotificationType.SUBMISSION,
      category: NotificationCategory.SYSTEM,
      title: 'Submission Accepted! 🎉',
      body: 'Your solution to "Reverse Linked List" has been accepted! Runtime: 0ms (100% faster). Memory: 44.5 MB.',
      link: '/submissions/sub-reverse-ll-001',
      metadata: {
        problem_slug: 'reverse-linked-list',
        status: 'ACCEPTED',
        runtime: 0,
        memory: 44500,
        percentile: 100,
      },
      is_read: false,
      read_at: null,
      created_at: hoursAgo(3),
    },
    {
      id: NOTIFICATION_IDS.SUB_WRONG_ANSWER,
      user_id: userIds[4] || userIds[0],
      type: NotificationType.SUBMISSION,
      category: NotificationCategory.SYSTEM,
      title: 'Wrong Answer on "Longest Palindromic Substring"',
      body: 'Your submission failed on test case 47/98. Hint: Consider edge cases with repeated characters.',
      link: '/problems/longest-palindromic-substring',
      metadata: {
        problem_slug: 'longest-palindromic-substring',
        status: 'WRONG_ANSWER',
        failed_test: 47,
        total_tests: 98,
      },
      is_read: false,
      read_at: null,
      created_at: hoursAgo(1),
    },
    {
      id: NOTIFICATION_IDS.SUB_TLE,
      user_id: userIds[5] || userIds[0],
      type: NotificationType.SUBMISSION,
      category: NotificationCategory.SYSTEM,
      title: 'Time Limit Exceeded on "Median of Two Sorted Arrays"',
      body: 'Your O(n*m) solution exceeded the time limit. Consider optimizing to O(log(n+m)) using binary search.',
      link: '/problems/median-of-two-sorted-arrays',
      metadata: {
        problem_slug: 'median-of-two-sorted-arrays',
        status: 'TIME_LIMIT_EXCEEDED',
        complexity: 'O(n*m)',
        suggested_complexity: 'O(log(n+m))',
      },
      is_read: true,
      read_at: hoursAgo(8),
      created_at: hoursAgo(10),
    },
    {
      id: NOTIFICATION_IDS.SUB_ACCEPTED_MEDIAN,
      user_id: userIds[6] || userIds[0],
      type: NotificationType.SUBMISSION,
      category: NotificationCategory.SYSTEM,
      title: 'Submission Accepted! 🎉',
      body: 'Your optimized solution to "Median of Two Sorted Arrays" has been accepted! Runtime: 3ms (99.8% faster).',
      link: '/submissions/sub-median-001',
      metadata: {
        problem_slug: 'median-of-two-sorted-arrays',
        status: 'ACCEPTED',
        runtime: 3,
        memory: 46800,
        percentile: 99.8,
      },
      is_read: false,
      read_at: null,
      created_at: hoursAgo(2),
    },
    // Contest notifications
    {
      id: NOTIFICATION_IDS.CONTEST_STARTING,
      user_id: userIds[7] || userIds[0],
      type: NotificationType.CONTEST,
      category: NotificationCategory.SYSTEM,
      title: 'Biweekly Contest 128 Starting in 30 Minutes',
      body: 'The contest you registered for is about to begin. Good luck!',
      link: '/contests/biweekly-128',
      metadata: { contest_id: 'biweekly-128', start_in_minutes: 30 },
      is_read: false,
      read_at: null,
      created_at: hoursAgo(0.5),
    },
    {
      id: NOTIFICATION_IDS.CONTEST_REGISTERED,
      user_id: userIds[8] || userIds[0],
      type: NotificationType.CONTEST,
      category: NotificationCategory.SYSTEM,
      title: 'Successfully Registered for Weekly Contest 423',
      body: 'You have been registered for Weekly Contest 423. The contest starts on Saturday at 10:30 AM UTC.',
      link: '/contests/weekly-423',
      metadata: { contest_id: 'weekly-423', registered_at: hoursAgo(24).toISOString() },
      is_read: true,
      read_at: hoursAgo(20),
      created_at: daysAgo(1),
    },
    {
      id: NOTIFICATION_IDS.CONTEST_RESULT,
      user_id: userIds[9] || userIds[0],
      type: NotificationType.CONTEST,
      category: NotificationCategory.SYSTEM,
      title: 'Contest Results: You ranked #15!',
      body: 'Congratulations! You solved 4/4 problems in Weekly Contest 422 and ranked #15 out of 12,453 participants.',
      link: '/contests/weekly-422/results',
      metadata: {
        contest_id: 'weekly-422',
        rank: 15,
        total_participants: 12453,
        problems_solved: 4,
        total_problems: 4,
      },
      is_read: false,
      read_at: null,
      created_at: daysAgo(2),
    },
    // Social notifications
    {
      id: NOTIFICATION_IDS.SOCIAL_COMMENT_REPLY,
      user_id: userIds[0],
      type: NotificationType.REPLY,
      category: NotificationCategory.COMMUNICATION,
      title: 'Someone replied to your comment',
      body: '"Great explanation! I also found that using a monotonic stack simplifies the solution significantly..."',
      link: '/solutions/two-sum-optimized#comment-42',
      metadata: {
        solution_id: 'sol-two-sum-optimized',
        comment_id: 42,
      },
      is_read: false,
      read_at: null,
      created_at: hoursAgo(4),
    },
    {
      id: NOTIFICATION_IDS.SOCIAL_MENTION,
      user_id: userIds[1] || userIds[0],
      type: NotificationType.MENTION,
      category: NotificationCategory.COMMUNICATION,
      title: 'You were mentioned in a solution discussion',
      body: 'A user mentioned you in a discussion about "Dynamic Programming optimization techniques"',
      link: '/forum/dp-optimization#post-128',
      metadata: {
        forum_post_id: 128,
        topic: 'Dynamic Programming optimization',
      },
      is_read: true,
      read_at: hoursAgo(6),
      created_at: hoursAgo(8),
    },
    {
      id: NOTIFICATION_IDS.SOCIAL_FOLLOW,
      user_id: userIds[2] || userIds[0],
      type: NotificationType.FOLLOW,
      category: NotificationCategory.COMMUNICATION,
      title: 'Someone started following you',
      body: 'A new user is now following your profile. Check out their achievements!',
      link: '/profile/follower',
      metadata: {},
      is_read: false,
      read_at: null,
      created_at: daysAgo(1),
    },
    {
      id: NOTIFICATION_IDS.SOCIAL_UPVOTE,
      user_id: userIds[3] || userIds[0],
      type: NotificationType.UPVOTE,
      category: NotificationCategory.COMMUNICATION,
      title: 'Your solution received 50 upvotes! 🔥',
      body: 'Your solution to "Binary Tree Maximum Path Sum" has received 50 upvotes from the community.',
      link: '/solutions/binary-tree-max-path-sum',
      metadata: {
        upvotes: 50,
        problem_slug: 'binary-tree-maximum-path-sum',
      },
      is_read: false,
      read_at: null,
      created_at: hoursAgo(12),
    },
  ];

  // Insert notifications
  for (const notification of notifications) {
    await prisma.notification.create({
      data: notification,
    });
  }

  console.log(`✅ Seeded ${notifications.length} notifications`);
}

main()
  .catch((e) => {
    console.error('❌ Seed failed:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
