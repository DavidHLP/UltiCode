import {
  IsEmail,
  IsNotEmpty,
  MinLength,
  IsOptional,
  IsEnum,
  IsString,
} from 'class-validator';
import type { UserEntity } from '@ulticode/shared-types';
import { UserRole } from '@ulticode/shared-types';

/**
 * Registration DTO
 * Extends shared UserEntity type for type consistency
 */
export class RegisterDto implements Partial<UserEntity> {
  @IsNotEmpty()
  @IsString()
  @MinLength(3)
  username!: string;

  @IsEmail()
  email!: string;

  @IsNotEmpty()
  @IsString()
  @MinLength(8)
  password!: string;

  @IsOptional()
  @IsString()
  avatar?: string;

  @IsOptional()
  @IsEnum(UserRole)
  role?: UserRole;
}
