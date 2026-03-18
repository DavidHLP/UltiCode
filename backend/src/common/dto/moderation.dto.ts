import {
  IsString,
  IsOptional,
  IsBoolean,
  IsEnum,
  IsInt,
  Min,
  IsDate,
  IsArray,
} from 'class-validator';
import { Type } from 'class-transformer';
import { PaginationDto } from './pagination.dto';
import { IsQueryBoolean } from '../decorators/boolean-transform.decorator';

// ============================================================================
// Enums (synced with Prisma schema)
// ============================================================================

export enum ReportCategory {
  SPAM = 'SPAM',
  HARASSMENT = 'HARASSMENT',
  HATE_SPEECH = 'HATE_SPEECH',
  VIOLENCE = 'VIOLENCE',
  SEXUAL_CONTENT = 'SEXUAL_CONTENT',
  MISINFORMATION = 'MISINFORMATION',
  WRONG_ANSWER = 'WRONG_ANSWER',
  COPYRIGHT = 'COPYRIGHT',
  OTHER = 'OTHER',
}

export enum ModerationActionType {
  DELETED = 'DELETED',
  HIDDEN = 'HIDDEN',
  RESTORED = 'RESTORED',
  WARNED = 'WARNED',
  TEMP_BANNED = 'TEMP_BANNED',
  PERM_BANNED = 'PERM_BANNED',
  DISMISSED = 'DISMISSED',
  RESOLVED = 'RESOLVED',
  APPEAL_PENDING = 'APPEAL_PENDING',
  APPEAL_APPROVED = 'APPEAL_APPROVED',
  APPEAL_REJECTED = 'APPEAL_REJECTED',
}

export enum ModerationStatus {
  PENDING = 'PENDING',
  UNDER_REVIEW = 'UNDER_REVIEW',
  RESOLVED = 'RESOLVED',
  DISMISSED = 'DISMISSED',
  APPEAL_PENDING = 'APPEAL_PENDING',
}

export enum ReportStatus {
  PENDING = 'PENDING',
  REVIEWED = 'REVIEWED',
  RESOLVED = 'RESOLVED',
  DISMISSED = 'DISMISSED',
}

export enum AppealStatus {
  PENDING = 'PENDING',
  UNDER_REVIEW = 'UNDER_REVIEW',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED',
}

export type ModeratableEntityType =
  | 'forum_post'
  | 'forum_comment'
  | 'solution'
  | 'solution_comment'
  | 'problem';

// ============================================================================
// Existing DTOs (kept for backward compatibility)
// ============================================================================

// Flag status query
export class FlaggedQueryDto {
  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_flagged?: boolean;
}

// Delete status query
export class DeletedQueryDto {
  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_deleted?: boolean;
}

// Combined query DTO - extends PaginationDto
export class ModeratedQueryDto extends PaginationDto {
  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_flagged?: boolean;

  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_deleted?: boolean;
}

// Flag operation DTO
export class FlagEntityDto {
  @IsString()
  @IsOptional()
  reason?: string;
}

// ============================================================================
// Report DTOs
// ============================================================================

// Create a new report
export class CreateReportDto {
  @IsString()
  entity_type: ModeratableEntityType;

  @IsString()
  entity_id: string;

  @IsEnum(ReportCategory)
  category: ReportCategory;

  @IsString()
  @IsOptional()
  reason?: string;

  @IsString()
  @IsOptional()
  evidence?: string;
}

// Query reports
export class QueryReportsDto extends PaginationDto {
  @IsEnum(ReportStatus)
  @IsOptional()
  status?: ReportStatus;

  @IsEnum(ReportCategory)
  @IsOptional()
  category?: ReportCategory;

  @IsString()
  @IsOptional()
  entity_type?: ModeratableEntityType;

  @IsString()
  @IsOptional()
  entity_id?: string;

  @IsString()
  @IsOptional()
  reporter_id?: string;
}

// ============================================================================
// Moderation Queue DTOs
// ============================================================================

// Query moderation queue
export class QueryModerationQueueDto extends PaginationDto {
  @IsEnum(ModerationStatus)
  @IsOptional()
  status?: ModerationStatus;

  @IsEnum(ReportCategory)
  @IsOptional()
  primary_category?: ReportCategory;

  @IsString()
  @IsOptional()
  entity_type?: ModeratableEntityType;

  @IsString()
  @IsOptional()
  assigned_to_id?: string;

  @IsInt()
  @Min(0)
  @IsOptional()
  min_priority?: number;
}

// Assign moderation to a user
export class AssignModerationDto {
  @IsString()
  assigned_to_id: string;
}

// Perform moderation action
export class PerformModerationActionDto {
  @IsEnum(ModerationActionType)
  action: ModerationActionType;

  @IsString()
  @IsOptional()
  note?: string;

  @IsInt()
  @Min(1)
  @IsOptional()
  duration_days?: number; // For temporary bans
}

// Batch moderation action
export class BatchModerationActionDto {
  @IsArray()
  @IsString({ each: true })
  queue_ids: string[];

  @IsEnum(ModerationActionType)
  action: ModerationActionType;

  @IsString()
  @IsOptional()
  note?: string;
}

// ============================================================================
// Appeal DTOs
// ============================================================================

// Create an appeal
export class CreateAppealDto {
  @IsString()
  queue_id: string;

  @IsString()
  reason: string;

  @IsString()
  @IsOptional()
  evidence?: string;
}

// Query appeals
export class QueryAppealsDto extends PaginationDto {
  @IsEnum(AppealStatus)
  @IsOptional()
  status?: AppealStatus;

  @IsString()
  @IsOptional()
  queue_id?: string;

  @IsString()
  @IsOptional()
  appellant_id?: string;
}

// Review an appeal
export class ReviewAppealDto {
  @IsEnum(AppealStatus)
  status: AppealStatus.APPROVED | AppealStatus.REJECTED;

  @IsString()
  @IsOptional()
  response?: string;
}

// ============================================================================
// User Warning DTOs
// ============================================================================

// Query user warnings
export class QueryUserWarningsDto extends PaginationDto {
  @IsString()
  @IsOptional()
  user_id?: string;

  @IsBoolean()
  @IsOptional()
  acknowledged?: boolean;
}

// Acknowledge a warning
export class AcknowledgeWarningDto {
  // No fields needed - just acknowledge
}

// ============================================================================
// User Ban DTOs
// ============================================================================

// Query user bans
export class QueryUserBansDto extends PaginationDto {
  @IsString()
  @IsOptional()
  user_id?: string;

  @IsBoolean()
  @IsOptional()
  active?: boolean;

  @IsBoolean()
  @IsOptional()
  is_permanent?: boolean;
}

// Create/Update user ban
export class CreateUserBanDto {
  @IsString()
  user_id: string;

  @IsBoolean()
  @IsOptional()
  is_permanent?: boolean;

  @IsString()
  reason: string;

  @IsEnum(ReportCategory)
  @IsOptional()
  category?: ReportCategory;

  @IsInt()
  @Min(1)
  @IsOptional()
  duration_days?: number; // For temporary bans

  @IsString()
  @IsOptional()
  queue_id?: string;

  @IsString()
  @IsOptional()
  action_id?: string;
}

// Revoke a ban
export class RevokeBanDto {
  @IsString()
  unban_reason: string;
}
