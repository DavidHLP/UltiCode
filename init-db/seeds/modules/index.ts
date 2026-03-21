// Base
export { BaseSeeder, createSeederExport } from './base/base.seeder';
export type { SeederFactory } from './base/base.seeder';

// L0 - No dependencies
export {
  SubmissionStatusesSeeder,
  createSubmissionStatusesSeeder,
} from './submission-statuses';
export { ProblemTagsSeeder, createProblemTagsSeeder } from './problem-tags';

// L1 - Base entities
export { UsersSeeder, createUsersSeeder, USER_IDS } from './users';

// L2 - Depends on L1
export { ProblemsSeeder, createProblemsSeeder, PROBLEM_IDS } from './problems';
export { ForumSeeder, createForumSeeder } from './forum';

// L3 - Depends on L2
export { ContestsSeeder, createContestsSeeder } from './contests';
export { SolutionsSeeder, createSolutionsSeeder } from './solutions';
export { ProblemListsSeeder, createProblemListsSeeder } from './problem-lists';
export {
  FlaggedProblemsSeeder,
  createFlaggedProblemsSeeder,
} from './flagged-problems';
export {
  ModerationQueueSeeder,
  createModerationQueueSeeder,
} from './moderation-queue';

// L4 - Depends on L3
export { SubmissionsSeeder, createSubmissionsSeeder } from './submissions';

// L5 - Depends on content
export { TranslationsSeeder, createTranslationsSeeder } from './translations';
export { PermissionsSeeder, createPermissionsSeeder } from './permissions';
export {
  NotificationsSeeder,
  createNotificationsSeeder,
} from './notifications';

// L6 - Recommendation data (depends on Problems, Users)
export {
  RecommendationSeeder,
  createRecommendationSeeder,
} from './recommendation';
