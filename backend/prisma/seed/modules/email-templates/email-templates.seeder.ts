import { PrismaClient } from '@prisma/client';
import { BaseSeeder } from '../base/base.seeder';
import { emailTemplatesData } from '../../data/email-templates.data';

export class EmailTemplatesSeeder extends BaseSeeder {
  constructor(prisma: PrismaClient) {
    super(prisma, 'email-templates');
  }

  async shouldSeed(): Promise<boolean> {
    const count = await this.prisma.emailTemplate.count();
    return count === 0;
  }

  async seed(): Promise<void> {
    this.logger.info('Seeding email templates...');

    for (const template of emailTemplatesData) {
      await this.prisma.emailTemplate.create({
        data: template,
      });
    }

    this.logger.success(`Created ${emailTemplatesData.length} email templates`);
  }

  async clean(): Promise<void> {
    this.logger.info('Cleaning email templates...');
    await this.prisma.emailTemplate.deleteMany();
    this.logger.success('Email templates cleaned');
  }
}
