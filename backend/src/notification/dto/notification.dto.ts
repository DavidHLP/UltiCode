import { IsOptional, IsBoolean, IsEnum } from 'class-validator';
import { Type } from 'class-transformer';
import { NotificationCategory, NotificationType } from '@prisma/client';
import { PaginationDto } from '../../common/dto/pagination.dto';

export class NotificationQueryDto extends PaginationDto {
  @IsOptional()
  @Type(() => Boolean)
  @IsBoolean()
  unreadOnly?: boolean;

  @IsOptional()
  @IsEnum(NotificationCategory)
  category?: NotificationCategory;

  @IsOptional()
  @IsEnum(NotificationType)
  type?: NotificationType;
}

export class UpdateNotificationDto {
  @IsBoolean()
  isRead: boolean;
}

export class UpdateNotificationPreferencesDto {
  @IsOptional()
  @IsBoolean()
  communication?: boolean;

  @IsOptional()
  @IsBoolean()
  marketing?: boolean;

  @IsOptional()
  @IsBoolean()
  security?: boolean;

  @IsOptional()
  @IsBoolean()
  system?: boolean;
}
