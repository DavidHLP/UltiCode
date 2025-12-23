// prisma/seed/seed-vote.ts
import { PrismaClient, EdgeOperationTargetType, EdgeOperationType } from '@prisma/client';
import voteData from './data/vote.data';

export async function clearVotes(prisma: PrismaClient): Promise<void> {
  await prisma.edgeOperation.deleteMany({
    where: {
      operation_type: { in: [EdgeOperationType.VOTE_UP, EdgeOperationType.VOTE_DOWN] },
    },
  });
}

export async function seedVotes(prisma: PrismaClient): Promise<{ votesCount: number }> {
  const votes = await prisma.edgeOperation.createMany({
    data: voteData.votes.map((vote) => ({
      target_id: vote.target_id,
      target_type: vote.target_type as EdgeOperationTargetType,
      operator_id: vote.user_id,
      operation_type: vote.operation_type as EdgeOperationType,
    })),
  });

  return {
    votesCount: votes.count,
  };
}
