// WebSocket event types for real-time notifications
export enum NotificationEvent {
  // Submission events
  SUBMISSION_RESULT = 'submission:result',
  SUBMISSION_STARTED = 'submission:started',

  // Contest events
  CONTEST_UPDATE = 'contest:update',
  CONTEST_RANKING_CHANGE = 'contest:ranking',
  CONTEST_STARTING = 'contest:starting',
  CONTEST_ENDED = 'contest:ended',

  // User interaction events
  MENTION_USER = 'mention:user',
  REPLY_TO_POST = 'post:reply',
  LIKE_SOLUTION = 'solution:like',

  // Achievement events
  BADGE_EARNED = 'badge:earned',
  MILESTONE_REACHED = 'milestone:reached',

  // System events
  SYSTEM_ANNOUNCEMENT = 'system:announcement',
  MAINTENANCE_WARNING = 'system:maintenance',
}

export interface SubmissionResultPayload {
  submissionId: string;
  problemId: string;
  problemSlug: string;
  status: string;
  runtime: number;
  memory: number;
}

export interface ContestUpdatePayload {
  contestId: string;
  type: 'ranking_change' | 'problem_solved' | 'contest_update';
  data: unknown;
}

export interface BadgeEarnedPayload {
  badgeId: string;
  badgeName: string;
  badgeDescription: string;
  earnedAt: string;
}

export interface NotificationPayload {
  id: string;
  type: string;
  title: string;
  body: string;
  link?: string;
  createdAt: string;
}

export interface WebSocketAuthPayload {
  userId: string;
}

export interface WebSocketMessage<T = unknown> {
  event: NotificationEvent;
  data: T;
  timestamp: number;
}
