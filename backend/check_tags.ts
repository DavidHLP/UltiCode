import { PrismaClient } from '@prisma/client';
const prisma = new PrismaClient();

async function main() {
  const tags = await prisma.problemTag.findMany();
  console.log(`Found ${tags.length} tags`);

  // Simple slugify function
  const slugify = (text) => text.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)+/g, '');

  for (const tag of tags) {
    const slug = slugify(tag.label);
    // Since we are running this BEFORE migration, we only update existing fields if they were there,
    // but the columns don't exist yet in the DB.
    // Wait, the columns DO NOT exist yet.
    // So we can't update them.
    // The problem is that when we migrate, Prisma tries to create a UNIQUE index on `slug`.
    // But `slug` is nullable in my definition: `slug String? @unique`
    // Wait, if it's nullable, then multiple nulls should be allowed in MySQL?
    // No, standard SQL says unique constraints allow multiple NULLs, but let's verify MySQL behavior.
    // In MySQL, unique key permits multiple NULL values.

    // However, the error message said:
    // "A unique constraint covering the columns `[slug]` on the table `problem_tags` will be added. If there are existing duplicate values, this will fail."

    // If all existing rows have NULL for slug (which they will initially), it should be fine in MySQL.
    // BUT, Prisma might be warning us just in case.

    // The REAL issue is the non-interactive mode.
    // Prisma asks for confirmation if data loss is possible or warnings exist.
    // We need to pass `--yes` or similar? No, `migrate dev` doesn't have --yes.
    // We should use `prisma db push` for prototyping or just try to force it.
    // Or we can delete all tags since this is a dev environment?
  }
}

main().catch(e => console.error(e)).finally(() => prisma.$disconnect());
