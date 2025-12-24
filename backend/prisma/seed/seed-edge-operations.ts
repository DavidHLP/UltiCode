import { PrismaClient } from '@prisma/client';

export async function clearEdgeOperations(prisma: PrismaClient): Promise<void> {
  // Since EdgeOperation has no dependent tables that need clearing first based on the provided schema (it's a leaf node relation-wise mostly),
  // we can just deleteMany.
  await prisma.edgeOperation.deleteMany();
  console.log('  🗑️  Cleared Edge Operations');
}
