import { IsEnum, IsNotEmpty, IsOptional, IsString, IsArray } from 'class-validator';
import { NotificationType, NotificationCategory } from '@prisma/client';

export enum NotificationTarget {
  ALL = 'ALL',
  USERS = 'USERS',
}

export class CreateNotificationDto {
  @IsString()
  @IsNotEmpty()
  title: string;

  @IsString()
  @IsNotEmpty()
  content: string;

  @IsEnum(NotificationType)
  @IsNotEmpty()
  type: NotificationType;

  @IsEnum(NotificationCategory)
  @IsOptional()
  category?: NotificationCategory = NotificationCategory.SYSTEM;

  @IsEnum(NotificationTarget)
  @IsNotEmpty()
  target: NotificationTarget;

  @IsArray()
  @IsString({ each: true })
  @IsOptional()
  userIds?: string[];
}
