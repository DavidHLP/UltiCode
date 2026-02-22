import { ApiProperty } from '@nestjs/swagger';
import {
  IsString,
  IsEmail,
  IsOptional,
  IsArray,
  IsEnum,
  IsObject,
} from 'class-validator';

export enum EmailStatus {
  PENDING = 'PENDING',
  SENT = 'SENT',
  FAILED = 'FAILED',
}

export class SendEmailDto {
  @ApiProperty({ description: 'Recipient email address' })
  @IsEmail()
  to: string;

  @ApiProperty({ description: 'Email subject' })
  @IsString()
  subject: string;

  @ApiProperty({ description: 'Email body (HTML)', required: false })
  @IsString()
  @IsOptional()
  html?: string;

  @ApiProperty({ description: 'Email body (plain text)', required: false })
  @IsString()
  @IsOptional()
  text?: string;

  @ApiProperty({ description: 'Template ID to use', required: false })
  @IsString()
  @IsOptional()
  templateId?: string;

  @ApiProperty({ description: 'Template variables', required: false })
  @IsObject()
  @IsOptional()
  variables?: Record<string, unknown>;
}

export class CreateTemplateDto {
  @ApiProperty({ description: 'Template name' })
  @IsString()
  name: string;

  @ApiProperty({ description: 'Email subject' })
  @IsString()
  subject: string;

  @ApiProperty({ description: 'Email body (HTML)' })
  @IsString()
  body: string;

  @ApiProperty({ description: 'Template variables', required: false })
  @IsArray()
  @IsOptional()
  variables?: string[];
}

export class UpdateTemplateDto {
  @ApiProperty({ description: 'Template name', required: false })
  @IsString()
  @IsOptional()
  name?: string;

  @ApiProperty({ description: 'Email subject', required: false })
  @IsString()
  @IsOptional()
  subject?: string;

  @ApiProperty({ description: 'Email body (HTML)', required: false })
  @IsString()
  @IsOptional()
  body?: string;

  @ApiProperty({ description: 'Template variables', required: false })
  @IsArray()
  @IsOptional()
  variables?: string[];
}

export class EmailQueryDto {
  @ApiProperty({
    description: 'Filter by status',
    enum: EmailStatus,
    required: false,
  })
  @IsEnum(EmailStatus)
  @IsOptional()
  status?: EmailStatus;

  @ApiProperty({ description: 'Filter by recipient', required: false })
  @IsEmail()
  @IsOptional()
  recipient?: string;

  @ApiProperty({ description: 'Page number', default: 1, required: false })
  @IsOptional()
  page?: number = 1;

  @ApiProperty({ description: 'Items per page', default: 20, required: false })
  @IsOptional()
  limit?: number = 20;
}

export class EmailTemplateResponseDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  name: string;

  @ApiProperty()
  subject: string;

  @ApiProperty()
  body: string;

  @ApiProperty({ required: false })
  variables?: string[];

  @ApiProperty()
  created_at: Date;

  @ApiProperty()
  updated_at: Date;
}

export class EmailLogResponseDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ required: false })
  template_id: string | null;

  @ApiProperty()
  recipient: string;

  @ApiProperty()
  subject: string;

  @ApiProperty({ enum: EmailStatus })
  status: EmailStatus;

  @ApiProperty({ required: false })
  sent_at: Date | null;

  @ApiProperty({ required: false })
  error: string | null;

  @ApiProperty()
  created_at: Date;
}

export class EmailListResponseDto {
  @ApiProperty({ type: [EmailLogResponseDto] })
  items: EmailLogResponseDto[];

  @ApiProperty()
  total: number;

  @ApiProperty()
  page: number;

  @ApiProperty()
  limit: number;
}

export class EmailStatsResponseDto {
  @ApiProperty()
  total: number;

  @ApiProperty()
  sent: number;

  @ApiProperty()
  pending: number;

  @ApiProperty()
  failed: number;
}
