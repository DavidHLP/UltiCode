import { PrismaClient } from '@prisma/client';
import { allTranslations } from './data/translations.data';

/**
 * Clear all translations from the database
 */
export async function clearTranslations(prisma: PrismaClient): Promise<void> {
  console.log('  Clearing translations...');
  await prisma.translation.deleteMany();
}

/**
 * Seed translations for i18n support
 */
export async function seedTranslations(
  prisma: PrismaClient,
): Promise<{ count: number }> {
  console.log('  Seeding translations...');

  await prisma.translation.createMany({
    data: allTranslations.map((t) => ({
      entity_type: t.entity_type,
      entity_id: t.entity_id,
      field_name: t.field_name,
      locale: t.locale,
      content: t.content,
    })),
    skipDuplicates: true,
  });

  return { count: allTranslations.length };
}
