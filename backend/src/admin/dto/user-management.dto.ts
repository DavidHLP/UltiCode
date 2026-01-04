import {
  IsString,
  IsEmail,
  IsOptional,
  IsEnum,
  IsBoolean,
  IsDateString,
  MaxLength,
  IsInt,
  Min,
} from 'class-validator';
import { UserRole } from '../../user/user.entity';
import { PermissionAction, PermissionResource } from '@prisma/client';

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

export class UserQueryDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsEnum(UserRole)
  @IsOptional()
  role?: UserRole;

  @IsBoolean()
  @IsOptional()
  is_active?: boolean;

  @IsBoolean()
  @IsOptional()
  is_banned?: boolean;

  @IsInt()
  @Min(1)
  @IsOptional()
  page?: number = 1;

  @IsInt()
  @Min(1)
  @IsOptional()
  limit?: number = 20;

  @IsString()
  @IsOptional()
  sortBy?: string = 'joined_at';

  @IsString()
  @IsOptional()
  sortOrder?: 'asc' | 'desc' = 'desc';
}
