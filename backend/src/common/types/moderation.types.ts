// Entity types that can be moderated
export type ModeratableEntityType =
  | 'forum_post'
  | 'forum_comment'
  | 'solution'
  | 'solution_comment'
  | 'problem';

// Report category enum values (synced with Prisma schema)
export const ReportCategoryValues = [
  'SPAM',
  'HARASSMENT',
  'HATE_SPEECH',
  'VIOLENCE',
  'SEXUAL_CONTENT',
  'MISINFORMATION',
  'WRONG_ANSWER',
  'COPYRIGHT',
  'OTHER',
] as const;

export type ReportCategory = (typeof ReportCategoryValues)[number];

// Moderation action type enum values (synced with Prisma schema)
export const ModerationActionTypeValues = [
  'DELETED',
  'HIDDEN',
  'RESTORED',
  'WARNED',
  'TEMP_BANNED',
  'PERM_BANNED',
  'DISMISSED',
  'RESOLVED',
  'APPEAL_PENDING',
  'APPEAL_APPROVED',
  'APPEAL_REJECTED',
] as const;

export type ModerationActionType = (typeof ModerationActionTypeValues)[number];

// Moderation status enum values (synced with Prisma schema)
export const ModerationStatusValues = [
  'PENDING',
  'UNDER_REVIEW',
  'RESOLVED',
  'DISMISSED',
  'APPEAL_PENDING',
] as const;

export type ModerationStatus = (typeof ModerationStatusValues)[number];

// Report status enum values (synced with Prisma schema)
export const ReportStatusValues = [
  'PENDING',
  'REVIEWED',
  'RESOLVED',
  'DISMISSED',
] as const;

export type ReportStatus = (typeof ReportStatusValues)[number];

// Appeal status enum values (synced with Prisma schema)
export const AppealStatusValues = [
  'PENDING',
  'UNDER_REVIEW',
  'APPROVED',
  'REJECTED',
] as const;

export type AppealStatus = (typeof AppealStatusValues)[number];

// Interface for entities that can be flagged (existing)
export interface FlaggableEntity {
  is_flagged: boolean;
  flagged_reason: string | null;
  flagged_at: Date | null;
}

// Interface for entities that can be soft-deleted (existing)
export interface SoftDeletableEntity {
  is_deleted: boolean;
  deleted_at: Date | null;
  deleted_by: string | null;
}

// Combined interface (existing)
export interface ModeratedEntity extends FlaggableEntity, SoftDeletableEntity {}

// Interface for moderatable entity with author info
export interface ModeratableEntity {
  id: string;
  author_id: string;
  is_flagged: boolean;
  flagged_reason?: string | null;
  is_deleted: boolean;
}

// Report creation input
export interface ReportCreateInput {
  entity_type: ModeratableEntityType;
  entity_id: string;
  category: ReportCategory;
  reason?: string;
  evidence?: string;
}

// Moderation action input
export interface ModerationActionInput {
  queue_id: string;
  action: ModerationActionType;
  note?: string;
  duration_days?: number; // For temp bans
}

// Appeal creation input
export interface AppealCreateInput {
  queue_id: string;
  reason: string;
  evidence?: string;
}

// Prisma update data types (existing)
export type FlagUpdateData = Pick<
  FlaggableEntity,
  'is_flagged' | 'flagged_at'
> & {
  flagged_reason?: string;
};

export type UnflagUpdateData = {
  is_flagged: false;
  flagged_at: null;
  flagged_reason: null;
};

export type SoftDeleteUpdateData = Pick<
  SoftDeletableEntity,
  'is_deleted' | 'deleted_at' | 'deleted_by'
>;

export type RestoreUpdateData = {
  is_deleted: false;
  deleted_at: null;
  deleted_by: null;
};

// Priority levels for moderation queue
export const ModerationPriority = {
  LOW: 0,
  NORMAL: 1,
  HIGH: 2,
  URGENT: 3,
} as const;

// Category to priority mapping
export const CategoryPriorityMap: Record<ReportCategory, number> = {
  SPAM: ModerationPriority.NORMAL,
  HARASSMENT: ModerationPriority.HIGH,
  HATE_SPEECH: ModerationPriority.HIGH,
  VIOLENCE: ModerationPriority.URGENT,
  SEXUAL_CONTENT: ModerationPriority.URGENT,
  MISINFORMATION: ModerationPriority.NORMAL,
  WRONG_ANSWER: ModerationPriority.LOW,
  COPYRIGHT: ModerationPriority.NORMAL,
  OTHER: ModerationPriority.LOW,
};

// Entity type to model name mapping
export const EntityTypeModelMap: Record<ModeratableEntityType, string> = {
  forum_post: 'forumPost',
  forum_comment: 'forumComment',
  solution: 'solution',
  solution_comment: 'solutionComment',
  problem: 'problem',
};
