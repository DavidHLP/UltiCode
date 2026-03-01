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

  // Community events
  COMMUNITY_NEW_POST = 'community:new_post',
  COMMUNITY_NEW_COMMENT = 'community:new_comment',
  COMMUNITY_POST_LIKED = 'community:post_liked',

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

export interface CommunityPostPayload {
  postId: string;
  postTitle: string;
  communityId: string;
  communityName: string;
  authorId: string;
  authorName: string;
  excerpt: string;
}

export interface CommunityCommentPayload {
  commentId: string;
  postId: string;
  postTitle: string;
  communityId: string;
  authorId: string;
  authorName: string;
  content: string;
}

export interface WebSocketAuthPayload {
  userId: string;
}

export interface WebSocketMessage<T = unknown> {
  event: NotificationEvent;
  data: T;
  timestamp: number;
}
