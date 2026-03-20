import { PrismaClient } from '@prisma/client';
import { BaseSeeder } from '../base/base.seeder';
import { emailTemplatesData } from '../../data/email-templates.data';
import type { SeedContext } from '../../core/seed-context';
import type { TransactionClient, SeedModuleResult, SeederMetadata } from '../../core/interfaces';

export class EmailTemplatesSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'email-templates',
    version: '1.0.0',
    dependencies: [],
    priority: 100,
    description: 'Seeds default email templates for notifications',
  };

  constructor(prisma: PrismaClient, context: SeedContext) {
    super(prisma, context);
  }

  async shouldSeed(): Promise<boolean> {
    const count = await this.prisma.emailTemplate.count();
    return count === 0;
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx);

    this.log('Seeding email templates...');

    let count = 0;
    for (const template of emailTemplatesData) {
      await client.emailTemplate.create({
        data: template,
      });
      count++;
    }

    this.log(`Created ${count} email templates`);

    return this.createResult(count, startTime);
  }

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx);

    this.log('Cleaning email templates...');
    await client.emailTemplate.deleteMany();
    this.log('Email templates cleaned');
  }
}
