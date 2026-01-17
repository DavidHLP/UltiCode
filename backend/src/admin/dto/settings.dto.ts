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
