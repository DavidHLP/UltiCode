import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { SystemSettingsDto } from '../dto/settings.dto';

@Injectable()
export class AdminSettingsService {
  constructor(private prisma: PrismaService) {}

  async getSettings(): Promise<SystemSettingsDto> {
    const settings = await this.prisma.systemSetting.findMany();
    const settingsMap = settings.reduce(
      (acc, curr) => {
        acc[curr.key] = curr.value;
        return acc;
      },
      {} as Record<string, string>,
    );

    return {
      maintenance_mode: settingsMap['maintenance_mode'] === 'true',
      maintenance_message:
        settingsMap['maintenance_message'] ||
        'Site is under maintenance. Please check back later.',
      enable_registrations: settingsMap['enable_registrations'] !== 'false', // Default true
      site_name: settingsMap['site_name'] || 'UltiCode',
      site_description:
        settingsMap['site_description'] || 'Competitive Programming Platform',
      require_email_verification:
        settingsMap['require_email_verification'] === 'true',
    };
  }

  async updateSettings(settingsDto: SystemSettingsDto) {
    const promises = Object.entries(settingsDto).map(([key, value]) => {
      if (value === undefined) return Promise.resolve();
      return this.prisma.systemSetting.upsert({
        where: { key },
        update: { value: String(value) },
        create: { key, value: String(value) },
      });
    });

    await Promise.all(promises);
    return this.getSettings();
  }
}
