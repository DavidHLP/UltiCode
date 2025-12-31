import { PrismaClient } from '@prisma/client';
import { SUBMISSION_STATUS_DEFINITIONS } from '../../src/submission/submission-statuses';

export async function clearSubmissionStatuses(prisma: PrismaClient) {
  console.log('  Clearing submission statuses...');
  try {
    await prisma.submissionStatus.deleteMany();
  } catch (error) {
    const err = error as { code?: string; message?: string };
    if (err?.code === 'P2021') {
      console.warn('  Skipping submission statuses (table missing).');
      return;
    }
    throw error;
  }
}

export async function seedSubmissionStatuses(prisma: PrismaClient) {
  console.log('  Seeding submission statuses...');
  const data = SUBMISSION_STATUS_DEFINITIONS.map((definition) => ({
    ...definition,
  }));
  try {
    await prisma.submissionStatus.createMany({
      data,
      skipDuplicates: true,
    });
    return { count: data.length };
  } catch (error) {
    const err = error as { code?: string; message?: string };
    if (err?.code === 'P2021') {
      console.warn('  Skipping submission statuses (table missing).');
      return { count: 0 };
    }
    throw error;
  }
}
