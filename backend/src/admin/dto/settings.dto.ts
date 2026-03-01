import {
  IsString,
  IsOptional,
  IsBoolean,
  IsEnum,
  IsArray,
} from 'class-validator';

export class SystemSettingsDto {
  @IsBoolean()
  @IsOptional()
  maintenance_mode?: boolean;

  @IsString()
  @IsOptional()
  maintenance_message?: string;

  @IsBoolean()
  @IsOptional()
  enable_registrations?: boolean;

  @IsString()
  @IsOptional()
  site_name?: string;

  @IsString()
  @IsOptional()
  site_description?: string;

  @IsBoolean()
  @IsOptional()
  require_email_verification?: boolean;
}

// Email Configuration Settings
export class EmailSettingsDto {
  @IsString()
  @IsOptional()
  smtp_host?: string;

  @IsString()
  @IsOptional()
  smtp_port?: string;

  @IsString()
  @IsOptional()
  smtp_user?: string;

  @IsString()
  @IsOptional()
  smtp_password?: string;

  @IsString()
  @IsOptional()
  smtp_from?: string;

  @IsString()
  @IsOptional()
  smtp_from_name?: string;

  @IsBoolean()
  @IsOptional()
  smtp_secure?: boolean;
}

// Rate Limit Settings
export class RateLimitSettingsDto {
  @IsString()
  @IsOptional()
  rate_limit_api?: string;

  @IsString()
  @IsOptional()
  rate_limit_submission?: string;

  @IsString()
  @IsOptional()
  rate_limit_auth?: string;

  @IsString()
  @IsOptional()
  rate_limit_upload?: string;
}

// Upload Settings
export class UploadSettingsDto {
  @IsString()
  @IsOptional()
  upload_max_size?: string;

  @IsString()
  @IsOptional()
  upload_allowed_types?: string;

  @IsString()
  @IsOptional()
  upload_max_files?: string;
}

// Feature Toggles
export class FeatureToggleDto {
  @IsBoolean()
  @IsOptional()
  feature_contest?: boolean;

  @IsBoolean()
  @IsOptional()
  feature_forum?: boolean;

  @IsBoolean()
  @IsOptional()
  feature_solutions?: boolean;

  @IsBoolean()
  @IsOptional()
  feature_subscriptions?: boolean;

  @IsBoolean()
  @IsOptional()
  feature_achievements?: boolean;

  @IsBoolean()
  @IsOptional()
  feature_notifications?: boolean;

  @IsBoolean()
  @IsOptional()
  feature_bookmarks?: boolean;

  @IsBoolean()
  @IsOptional()
  feature_problem_lists?: boolean;
}

// Combined settings response
export class AllSettingsDto {
  // General
  maintenance_mode?: boolean;
  maintenance_message?: string;
  enable_registrations?: boolean;
  site_name?: string;
  site_description?: string;
  require_email_verification?: boolean;

  // Email
  smtp_host?: string;
  smtp_port?: string;
  smtp_user?: string;
  smtp_password?: string;
  smtp_from?: string;
  smtp_from_name?: string;
  smtp_secure?: boolean;

  // Rate Limits
  rate_limit_api?: string;
  rate_limit_submission?: string;
  rate_limit_auth?: string;
  rate_limit_upload?: string;

  // Uploads
  upload_max_size?: string;
  upload_allowed_types?: string;
  upload_max_files?: string;

  // Feature Toggles
  feature_contest?: boolean;
  feature_forum?: boolean;
  feature_solutions?: boolean;
  feature_subscriptions?: boolean;
  feature_achievements?: boolean;
  feature_notifications?: boolean;
  feature_bookmarks?: boolean;
  feature_problem_lists?: boolean;
}

export class MaintenanceModeDto {
  @IsBoolean()
  enabled: boolean;

  @IsString()
  @IsOptional()
  message?: string;
}

export class BulkActionDto {
  @IsArray()
  @IsString({ each: true })
  ids: string[];
}

export class BulkBanDto extends BulkActionDto {
  @IsString()
  reason: string;

  @IsString()
  @IsOptional()
  until?: string;
}

export class BulkRoleDto extends BulkActionDto {
  @IsEnum(['USER', 'MODERATOR', 'ADMIN'])
  role: 'USER' | 'MODERATOR' | 'ADMIN';
}

export class BulkProblemActionDto extends BulkActionDto {
  @IsEnum(['publish', 'unpublish', 'delete', 'restore'])
  action: 'publish' | 'unpublish' | 'delete' | 'restore';
}

export class BulkSolutionActionDto extends BulkActionDto {
  @IsEnum(['delete', 'unflag', 'publish', 'unpublish'])
  action: 'delete' | 'unflag' | 'publish' | 'unpublish';
}

export class BulkForumActionDto extends BulkActionDto {
  @IsEnum(['delete', 'unflag'])
  action: 'delete' | 'unflag';
}

export class BulkEditProblemDto extends BulkActionDto {
  @IsEnum(['EASY', 'MEDIUM', 'HARD'])
  @IsOptional()
  difficulty?: 'EASY' | 'MEDIUM' | 'HARD';

  @IsBoolean()
  @IsOptional()
  is_premium?: boolean;
}

export class FlagProblemDto {
  @IsString()
  problemId: string;

  @IsString()
  reason: string;

  @IsString()
  @IsOptional()
  reporterId?: string;
}

export class ModerationActionDto {
  @IsString()
  problemId: string;

  @IsEnum(['PENDING', 'REVIEWED', 'RESOLVED', 'DISMISSED'])
  status: 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED';

  @IsString()
  @IsOptional()
  notes?: string;

  @IsString()
  moderatorId: string;
}
