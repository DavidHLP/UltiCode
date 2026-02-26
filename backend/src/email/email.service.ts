import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaService } from '../prisma.service';
import {
  SendEmailDto,
  CreateTemplateDto,
  UpdateTemplateDto,
  EmailQueryDto,
  EmailTemplateResponseDto,
  EmailLogResponseDto,
  EmailListResponseDto,
  EmailStatsResponseDto,
  EmailStatus,
} from './dto/email.dto';
import * as nodemailer from 'nodemailer';
import type { Transporter } from 'nodemailer';

@Injectable()
export class EmailService {
  private readonly logger = new Logger(EmailService.name);
  private transporter: Transporter | null = null;

  constructor(
    private prisma: PrismaService,
    private configService: ConfigService,
  ) {
    this.initializeTransporter();
  }

  private initializeTransporter(): void {
    const host = this.configService.get('SMTP_HOST');
    const port = this.configService.get('SMTP_PORT');
    const user = this.configService.get('SMTP_USER');
    const pass = this.configService.get('SMTP_PASSWORD');

    if (host && port && user && pass) {
      this.transporter = nodemailer.createTransport({
        host,
        port: Number(port),
        secure: Number(port) === 465,
        auth: { user, pass },
      });
      this.logger.log('SMTP transporter initialized');
    } else {
      this.logger.warn('SMTP not configured. Emails will be logged only.');
    }
  }

  async sendEmail(dto: SendEmailDto): Promise<EmailLogResponseDto> {
    let html = dto.html;
    const text = dto.text;
    let subject = dto.subject;

    // If template is specified, use it
    if (dto.templateId) {
      const template = await this.prisma.emailTemplate.findUnique({
        where: { id: dto.templateId },
      });

      if (template) {
        subject = this.renderTemplate(template.subject, dto.variables ?? {});
        html = this.renderTemplate(template.body, dto.variables ?? {});
      }
    }

    // Create log entry
    const log = await this.prisma.emailLog.create({
      data: {
        template_id: dto.templateId,
        recipient: dto.to,
        subject,
        status: EmailStatus.PENDING,
      },
    });

    try {
      if (this.transporter) {
        const from = this.configService.get('SMTP_FROM', 'noreply@example.com');
        const fromName = this.configService.get('SMTP_FROM_NAME', 'UltiCode');

        await this.transporter.sendMail({
          from: `"${fromName}" <${from}>`,
          to: dto.to,
          subject,
          html,
          text,
        });
      } else {
        // Log only mode
        this.logger.log(`[EMAIL] To: ${dto.to}, Subject: ${subject}`);
      }

      // Update log as sent
      const updatedLog = await this.prisma.emailLog.update({
        where: { id: log.id },
        data: {
          status: EmailStatus.SENT,
          sent_at: new Date(),
        },
      });

      return this.toLogResponse(updatedLog);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';

      // Update log as failed
      const updatedLog = await this.prisma.emailLog.update({
        where: { id: log.id },
        data: {
          status: EmailStatus.FAILED,
          error: message,
        },
      });

      this.logger.error(`Failed to send email: ${message}`);
      return this.toLogResponse(updatedLog);
    }
  }

  private renderTemplate(
    template: string,
    variables: Record<string, unknown>,
  ): string {
    let result = template;
    for (const [key, value] of Object.entries(variables)) {
      result = result.replace(new RegExp(`{{${key}}}`, 'g'), String(value));
    }
    return result;
  }

  // Template CRUD
  async createTemplate(
    dto: CreateTemplateDto,
  ): Promise<EmailTemplateResponseDto> {
    const template = await this.prisma.emailTemplate.create({
      data: {
        name: dto.name,
        subject: dto.subject,
        body: dto.body,
        variables: dto.variables ?? [],
      },
    });
    return this.toTemplateResponse(template);
  }

  async findAllTemplates(): Promise<EmailTemplateResponseDto[]> {
    const templates = await this.prisma.emailTemplate.findMany({
      orderBy: { name: 'asc' },
    });
    return templates.map((t) => this.toTemplateResponse(t));
  }

  async findOneTemplate(id: string): Promise<EmailTemplateResponseDto> {
    const template = await this.prisma.emailTemplate.findUnique({
      where: { id },
    });

    if (!template) {
      throw new Error('Template not found');
    }

    return this.toTemplateResponse(template);
  }

  async updateTemplate(
    id: string,
    dto: UpdateTemplateDto,
  ): Promise<EmailTemplateResponseDto> {
    const template = await this.prisma.emailTemplate.update({
      where: { id },
      data: {
        name: dto.name,
        subject: dto.subject,
        body: dto.body,
        variables: dto.variables,
      },
    });
    return this.toTemplateResponse(template);
  }

  async removeTemplate(id: string): Promise<{ success: boolean }> {
    await this.prisma.emailTemplate.delete({
      where: { id },
    });
    return { success: true };
  }

  // Email logs
  async findAllLogs(query: EmailQueryDto): Promise<EmailListResponseDto> {
    const where: any = {};

    if (query.status) {
      where.status = query.status;
    }

    if (query.recipient) {
      where.recipient = { contains: query.recipient };
    }

    const page = query.page ?? 1;
    const limit = query.limit ?? 20;

    const [total, items] = await Promise.all([
      this.prisma.emailLog.count({ where }),
      this.prisma.emailLog.findMany({
        where,
        orderBy: { created_at: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
      }),
    ]);

    return {
      items: items.map((l) => this.toLogResponse(l)),
      total,
      page,
      limit,
    };
  }

  async getStats(): Promise<EmailStatsResponseDto> {
    const [total, sent, pending, failed] = await Promise.all([
      this.prisma.emailLog.count(),
      this.prisma.emailLog.count({ where: { status: EmailStatus.SENT } }),
      this.prisma.emailLog.count({ where: { status: EmailStatus.PENDING } }),
      this.prisma.emailLog.count({ where: { status: EmailStatus.FAILED } }),
    ]);

    return { total, sent, pending, failed };
  }

  private toTemplateResponse(template: any): EmailTemplateResponseDto {
    return {
      id: template.id,
      name: template.name,
      subject: template.subject,
      body: template.body,
      variables: template.variables,
      created_at: template.created_at,
      updated_at: template.updated_at,
    };
  }

  private toLogResponse(log: any): EmailLogResponseDto {
    return {
      id: log.id,
      template_id: log.template_id,
      recipient: log.recipient,
      subject: log.subject,
      status: log.status,
      sent_at: log.sent_at,
      error: log.error,
      created_at: log.created_at,
    };
  }
}
