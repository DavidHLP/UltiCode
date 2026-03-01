import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import {
  SystemSettingsDto,
  EmailSettingsDto,
  RateLimitSettingsDto,
  UploadSettingsDto,
  FeatureToggleDto,
  AllSettingsDto,
} from '../dto/settings.dto';

// Default settings values
const DEFAULT_SETTINGS: Record<string, string> = {
  // General
  maintenance_mode: 'false',
  maintenance_message: 'Site is under maintenance. Please check back later.',
  enable_registrations: 'true',
  site_name: 'UltiCode',
  site_description: 'Competitive Programming Platform',
  require_email_verification: 'false',

  // Email
  smtp_host: '',
  smtp_port: '587',
  smtp_user: '',
  smtp_password: '',
  smtp_from: 'noreply@ulticode.com',
  smtp_from_name: 'UltiCode',
  smtp_secure: 'true',

  // Rate Limits
  rate_limit_api: '100',
  rate_limit_submission: '10',
  rate_limit_auth: '5',
  rate_limit_upload: '20',

  // Uploads
  upload_max_size: '10485760', // 10MB in bytes
  upload_allowed_types: 'jpg,jpeg,png,gif,pdf,zip',
  upload_max_files: '5',

  // Feature Toggles (default all enabled)
  feature_contest: 'true',
  feature_forum: 'true',
  feature_solutions: 'true',
  feature_subscriptions: 'true',
  feature_achievements: 'true',
  feature_notifications: 'true',
  feature_bookmarks: 'true',
  feature_problem_lists: 'true',
};

@Injectable()
export class AdminSettingsService {
  constructor(private prisma: PrismaService) {}

  async getAllSettings(): Promise<AllSettingsDto> {
    const settings = await this.prisma.systemSetting.findMany();
    const settingsMap = settings.reduce(
      (acc, curr) => {
        acc[curr.key] = curr.value;
        return acc;
      },
      {} as Record<string, string>,
    );

    // Merge with defaults
    const merged = { ...DEFAULT_SETTINGS, ...settingsMap };

    return {
      // General
      maintenance_mode: merged['maintenance_mode'] === 'true',
      maintenance_message: merged['maintenance_message'],
      enable_registrations: merged['enable_registrations'] === 'true',
      site_name: merged['site_name'],
      site_description: merged['site_description'],
      require_email_verification:
        merged['require_email_verification'] === 'true',

      // Email
      smtp_host: merged['smtp_host'],
      smtp_port: merged['smtp_port'],
      smtp_user: merged['smtp_user'],
      smtp_password: merged['smtp_password'],
      smtp_from: merged['smtp_from'],
      smtp_from_name: merged['smtp_from_name'],
      smtp_secure: merged['smtp_secure'] === 'true',

      // Rate Limits
      rate_limit_api: merged['rate_limit_api'],
      rate_limit_submission: merged['rate_limit_submission'],
      rate_limit_auth: merged['rate_limit_auth'],
      rate_limit_upload: merged['rate_limit_upload'],

      // Uploads
      upload_max_size: merged['upload_max_size'],
      upload_allowed_types: merged['upload_allowed_types'],
      upload_max_files: merged['upload_max_files'],

      // Feature Toggles
      feature_contest: merged['feature_contest'] === 'true',
      feature_forum: merged['feature_forum'] === 'true',
      feature_solutions: merged['feature_solutions'] === 'true',
      feature_subscriptions: merged['feature_subscriptions'] === 'true',
      feature_achievements: merged['feature_achievements'] === 'true',
      feature_notifications: merged['feature_notifications'] === 'true',
      feature_bookmarks: merged['feature_bookmarks'] === 'true',
      feature_problem_lists: merged['feature_problem_lists'] === 'true',
    };
  }

  async getSettings(): Promise<SystemSettingsDto> {
    const all = await this.getAllSettings();
    return {
      maintenance_mode: all.maintenance_mode,
      maintenance_message: all.maintenance_message,
      enable_registrations: all.enable_registrations,
      site_name: all.site_name,
      site_description: all.site_description,
      require_email_verification: all.require_email_verification,
    };
  }

  async getEmailSettings(): Promise<EmailSettingsDto> {
    const all = await this.getAllSettings();
    return {
      smtp_host: all.smtp_host,
      smtp_port: all.smtp_port,
      smtp_user: all.smtp_user,
      smtp_password: all.smtp_password,
      smtp_from: all.smtp_from,
      smtp_from_name: all.smtp_from_name,
      smtp_secure: all.smtp_secure,
    };
  }

  async getRateLimitSettings(): Promise<RateLimitSettingsDto> {
    const all = await this.getAllSettings();
    return {
      rate_limit_api: all.rate_limit_api,
      rate_limit_submission: all.rate_limit_submission,
      rate_limit_auth: all.rate_limit_auth,
      rate_limit_upload: all.rate_limit_upload,
    };
  }

  async getUploadSettings(): Promise<UploadSettingsDto> {
    const all = await this.getAllSettings();
    return {
      upload_max_size: all.upload_max_size,
      upload_allowed_types: all.upload_allowed_types,
      upload_max_files: all.upload_max_files,
    };
  }

  async getFeatureToggles(): Promise<FeatureToggleDto> {
    const all = await this.getAllSettings();
    return {
      feature_contest: all.feature_contest,
      feature_forum: all.feature_forum,
      feature_solutions: all.feature_solutions,
      feature_subscriptions: all.feature_subscriptions,
      feature_achievements: all.feature_achievements,
      feature_notifications: all.feature_notifications,
      feature_bookmarks: all.feature_bookmarks,
      feature_problem_lists: all.feature_problem_lists,
    };
  }

  async updateSettings(settingsDto: Partial<AllSettingsDto>) {
    const promises = Object.entries(settingsDto).map(([key, value]) => {
      if (value === undefined) return Promise.resolve();
      return this.prisma.systemSetting.upsert({
        where: { key },
        update: { value: String(value) },
        create: { key, value: String(value) },
      });
    });

    await Promise.all(promises);
    return this.getAllSettings();
  }

  async resetToDefaults(): Promise<AllSettingsDto> {
    const promises = Object.entries(DEFAULT_SETTINGS).map(([key, value]) => {
      return this.prisma.systemSetting.upsert({
        where: { key },
        update: { value },
        create: { key, value },
      });
    });

    await Promise.all(promises);
    return this.getAllSettings();
  }
}
