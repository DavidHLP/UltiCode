import { ApiProperty } from '@nestjs/swagger';
import { IsEnum, IsOptional, IsBoolean, IsString } from 'class-validator';

export enum BackupType {
  FULL = 'FULL',
  PARTIAL = 'PARTIAL',
}

export enum BackupStatus {
  PENDING = 'PENDING',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
}

export class CreateBackupDto {
  @ApiProperty({
    description: 'Type of backup',
    enum: BackupType,
    default: BackupType.FULL,
  })
  @IsEnum(BackupType)
  @IsOptional()
  type?: BackupType;

  @ApiProperty({ description: 'Description for this backup', required: false })
  @IsString()
  @IsOptional()
  description?: string;
}

export class RestoreBackupDto {
  @ApiProperty({ description: 'Confirm the restore operation' })
  @IsBoolean()
  confirm: boolean;
}

export class BackupQueryDto {
  @ApiProperty({
    description: 'Filter by status',
    enum: BackupStatus,
    required: false,
  })
  @IsEnum(BackupStatus)
  @IsOptional()
  status?: BackupStatus;

  @ApiProperty({
    description: 'Filter by type',
    enum: BackupType,
    required: false,
  })
  @IsEnum(BackupType)
  @IsOptional()
  type?: BackupStatus;

  @ApiProperty({ description: 'Page number', default: 1, required: false })
  @IsOptional()
  page?: number = 1;

  @ApiProperty({ description: 'Items per page', default: 20, required: false })
  @IsOptional()
  limit?: number = 20;
}

export class BackupResponseDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  filename: string;

  @ApiProperty({ type: 'integer' })
  size: number;

  @ApiProperty({ enum: BackupType })
  type: BackupType;

  @ApiProperty({ enum: BackupStatus })
  status: BackupStatus;

  @ApiProperty()
  created_by: string;

  @ApiProperty()
  created_at: Date;

  @ApiProperty({ nullable: true })
  completed_at: Date | null;

  @ApiProperty({ nullable: true })
  error: string | null;

  @ApiProperty({ nullable: true })
  metadata: Record<string, unknown> | null;
}

export class BackupListResponseDto {
  @ApiProperty({ type: [BackupResponseDto] })
  items: BackupResponseDto[];

  @ApiProperty()
  total: number;

  @ApiProperty()
  page: number;

  @ApiProperty()
  limit: number;
}
