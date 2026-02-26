import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Param,
  Body,
  Query,
  UseGuards,
} from '@nestjs/common';
import {
  ApiTags,
  ApiOperation,
  ApiResponse,
  ApiBearerAuth,
} from '@nestjs/swagger';
import { EmailService } from './email.service';
import {
  SendEmailDto,
  CreateTemplateDto,
  UpdateTemplateDto,
  EmailQueryDto,
  EmailTemplateResponseDto,
  EmailListResponseDto,
  EmailStatsResponseDto,
} from './dto/email.dto';
import { AuthGuard } from '../auth/auth.guard';

@ApiTags('Admin - Email')
@ApiBearerAuth()
@UseGuards(AuthGuard)
@Controller('admin/email')
export class EmailController {
  constructor(private readonly emailService: EmailService) {}

  // Email sending
  @Post('send')
  @ApiOperation({ summary: 'Send an email' })
  @ApiResponse({ status: 201, description: 'Email sent' })
  async sendEmail(@Body() dto: SendEmailDto) {
    return this.emailService.sendEmail(dto);
  }

  @Get('logs')
  @ApiOperation({ summary: 'List email logs' })
  @ApiResponse({ status: 200, type: EmailListResponseDto })
  async getLogs(@Query() query: EmailQueryDto): Promise<EmailListResponseDto> {
    return this.emailService.findAllLogs(query);
  }

  @Get('stats')
  @ApiOperation({ summary: 'Get email statistics' })
  @ApiResponse({ status: 200, type: EmailStatsResponseDto })
  async getStats(): Promise<EmailStatsResponseDto> {
    return this.emailService.getStats();
  }

  // Templates
  @Post('templates')
  @ApiOperation({ summary: 'Create an email template' })
  @ApiResponse({ status: 201, type: EmailTemplateResponseDto })
  async createTemplate(
    @Body() dto: CreateTemplateDto,
  ): Promise<EmailTemplateResponseDto> {
    return this.emailService.createTemplate(dto);
  }

  @Get('templates')
  @ApiOperation({ summary: 'List all email templates' })
  @ApiResponse({ status: 200, type: [EmailTemplateResponseDto] })
  async getTemplates(): Promise<EmailTemplateResponseDto[]> {
    return this.emailService.findAllTemplates();
  }

  @Get('templates/:id')
  @ApiOperation({ summary: 'Get email template by ID' })
  @ApiResponse({ status: 200, type: EmailTemplateResponseDto })
  async getTemplate(
    @Param('id') id: string,
  ): Promise<EmailTemplateResponseDto> {
    return this.emailService.findOneTemplate(id);
  }

  @Put('templates/:id')
  @ApiOperation({ summary: 'Update email template' })
  @ApiResponse({ status: 200, type: EmailTemplateResponseDto })
  async updateTemplate(
    @Param('id') id: string,
    @Body() dto: UpdateTemplateDto,
  ): Promise<EmailTemplateResponseDto> {
    return this.emailService.updateTemplate(id, dto);
  }

  @Delete('templates/:id')
  @ApiOperation({ summary: 'Delete email template' })
  @ApiResponse({ status: 200 })
  async deleteTemplate(@Param('id') id: string): Promise<{ success: boolean }> {
    return this.emailService.removeTemplate(id);
  }
}
