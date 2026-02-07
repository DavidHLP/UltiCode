import type { PrismaClient } from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import { allTranslations } from '../../data/translations.data';

/**
 * Translations seeder - creates i18n translations for content.
 *
 * Layer: L5 (depends on all content modules)
 */
export class TranslationsSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'Translations',
    version: '1.0.0',
    dependencies: ['Problems', 'Contests', 'Forum'],
    priority: 0,
    description: 'Seed i18n translations',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;
    await client.translation.deleteMany();
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;

    const translationData = allTranslations.map((t) => ({
      entity_type: t.entity_type,
      entity_id: t.entity_id,
      field_name: t.field_name,
      locale: t.locale,
      content: t.content,
    }));

    const result = await client.translation.createMany({
      data: translationData,
      skipDuplicates: true,
    });

    return this.createResult(result.count, startTime);
  }
}

export const createTranslationsSeeder = createSeederExport(TranslationsSeeder);
