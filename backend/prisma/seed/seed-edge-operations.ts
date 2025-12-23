import { PrismaClient, EdgeOperationType, EdgeOperationTargetType } from '@prisma/client';
import {
  OPERATION_WEIGHTS,
  MAX_OPERATIONS_PER_USER,
} from './data/edge-operations-data';

export async function clearEdgeOperations(prisma: PrismaClient): Promise<void> {
  // Since EdgeOperation has no dependent tables that need clearing first based on the provided schema (it's a leaf node relation-wise mostly),
  // we can just deleteMany.
  await prisma.edgeOperation.deleteMany();
  console.log('  🗑️  Cleared Edge Operations');
}

export async function seedEdgeOperations(prisma: PrismaClient): Promise<void> {
  console.log('  Testing Edge Operations seeding...');

  // 1. Fetch potential targets
  // We'll limit to a subset to keep seeding fast, or fetch IDs.
  const users = await prisma.user.findMany({ select: { id: true }, take: 50 });
  if (users.length === 0) {
    console.log('    ℹ️  No users found, skipping edge operations seeding.');
    return;
  }

  const solutions = await prisma.solution.findMany({ select: { id: true }, take: 100 });
  const forumPosts = await prisma.forumPost.findMany({ select: { id: true }, take: 100 });
  const problems = await prisma.problem.findMany({ select: { id: true }, take: 100 });
  // Note: We are not seeding operations on comments to keep it simple, but we could.

  const allTargets: { id: string; type: EdgeOperationTargetType }[] = [
    ...solutions.map((s) => ({ id: s.id, type: EdgeOperationTargetType.SOLUTION })),
    ...forumPosts.map((p) => ({ id: p.id, type: EdgeOperationTargetType.FORUM_POST })),
    ...problems.map((p) => ({ id: p.id.toString(), type: EdgeOperationTargetType.PROBLEM })),
  ];

  if (allTargets.length === 0) {
    console.log('    ℹ️  No targets found, skipping edge operations seeding.');
    return;
  }

  let totalOperations = 0;

  // 2. Generate operations
  for (const user of users) {
    // Randomly decide how many operations this user performed
    const opsCount = Math.floor(Math.random() * MAX_OPERATIONS_PER_USER) + 1;
    
    // Shuffle targets to pick random ones
    const shuffledTargets = [...allTargets].sort(() => 0.5 - Math.random());
    const selectedTargets = shuffledTargets.slice(0, opsCount);

    for (const target of selectedTargets) {
      const opType = pickWeightedOperation();
      
      try {
        await prisma.edgeOperation.create({
          data: {
            target_id: target.id,
            target_type: target.type,
            operator_id: user.id,
            operation_type: opType,
          },
        });
        totalOperations++;
      } catch {
        // Ignore unique constraint violations (duplicate op)
        // console.warn('Duplicate operation ignored');
      }
    }
  }

  console.log(`  ✓ Edge Operations: ${totalOperations} records created`);
}

function pickWeightedOperation(): EdgeOperationType {
  const rand = Math.random() * 100;
  let sum = 0;
  for (const [op, weight] of Object.entries(OPERATION_WEIGHTS)) {
    sum += weight;
    if (rand < sum) return op as EdgeOperationType;
  }
  return EdgeOperationType.VOTE_UP; // Fallback
}
