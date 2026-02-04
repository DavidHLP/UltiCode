import {
  IsString,
  IsEmail,
  IsOptional,
  IsEnum,
  IsBoolean,
  IsDateString,
  MaxLength,
} from 'class-validator';
import { UserRole } from '../../user/user.service';
import { PermissionAction, PermissionResource } from '@prisma/client';
import { PaginationDto } from '../../common/dto/pagination.dto';
import { IsQueryBoolean } from '../../common/decorators/boolean-transform.decorator';

/**
 * Escape SQL LIKE wildcards to prevent wildcard injection
 * Users should not be able to inject % or _ to manipulate search results
 */
function escapeLikeWildcard(value: string): string {
  return value.replace(/[%_\\]/g, '\\$&');
}

export class CreateUserDto {
  @IsString()
  @MaxLength(120)
  username: string;

  @IsEmail()
  @MaxLength(255)
  email: string;

  @IsString()
  @MaxLength(120)
  name: string;

  @IsString()
  @MaxLength(255)
  @IsOptional()
  password?: string;

  @IsEnum(UserRole)
  @IsOptional()
  role?: UserRole;

  @IsBoolean()
  @IsOptional()
  is_active?: boolean;
}

export class UpdateUserDto {
  @IsString()
  @MaxLength(120)
  @IsOptional()
  username?: string;

  @IsEmail()
  @MaxLength(255)
  @IsOptional()
  email?: string;

  @IsString()
  @MaxLength(120)
  @IsOptional()
  name?: string;

  @IsEnum(UserRole)
  @IsOptional()
  role?: UserRole;

  @IsBoolean()
  @IsOptional()
  is_active?: boolean;

  @IsString()
  @MaxLength(255)
  @IsOptional()
  avatar?: string;

  @IsString()
  @IsOptional()
  bio?: string;

  @IsString()
  @MaxLength(255)
  @IsOptional()
  company?: string;

  @IsString()
  @MaxLength(255)
  @IsOptional()
  github?: string;

  @IsString()
  @MaxLength(255)
  @IsOptional()
  website?: string;

  @IsString()
  @MaxLength(255)
  @IsOptional()
  location?: string;

  @IsString()
  @MaxLength(255)
  @IsOptional()
  twitter?: string;

  @IsString()
  @MaxLength(50)
  @IsOptional()
  preferred_language?: string;
}

export class BanUserDto {
  @IsString()
  @IsOptional()
  reason?: string;

  @IsDateString()
  @IsOptional()
  until?: string;
}

export class GrantPermissionDto {
  @IsEnum(PermissionAction)
  action: PermissionAction;

  @IsEnum(PermissionResource)
  resource: PermissionResource;

  @IsDateString()
  @IsOptional()
  expires_at?: string;
}

export class BulkActionDto {
  @IsString({ each: true })
  ids: string[];

  @IsString()
  @IsOptional()
  reason?: string;

  @IsEnum(UserRole)
  @IsOptional()
  role?: UserRole;
}

export class ResetPasswordDto {
  @IsString()
  @MaxLength(255)
  password: string;
}

export class UserQueryDto extends PaginationDto {
  @IsString()
  @MaxLength(100)
  @IsOptional()
  search?: string;

  @IsEnum(UserRole)
  @IsOptional()
  role?: UserRole;

  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_active?: boolean;

  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_banned?: boolean;

  @IsString()
  @IsOptional()
  override sortBy?: string = 'joined_at';

  /**
   * Get sanitized search term with wildcards escaped
   * Safe to use in SQL LIKE queries
   */
  getSanitizedSearch(): string | undefined {
    return this.search ? escapeLikeWildcard(this.search) : undefined;
  }
}
