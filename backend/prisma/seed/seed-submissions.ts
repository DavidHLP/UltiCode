import { PrismaClient } from '@prisma/client';
import { SUBMISSIONS } from './data/submissions.data';

export async function clearSubmissions(prisma: PrismaClient) {
  console.log('  🗑️ Clearing submissions...');
  await prisma.submission.deleteMany();
}

export async function seedSubmissions(prisma: PrismaClient) {
  console.log('  🌱 Seeding submissions...');
  for (const sub of SUBMISSIONS) {
    await prisma.submission.create({ data: sub });
  }
  return { count: SUBMISSIONS.length };
}
