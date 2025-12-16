// prisma/seed/seed-vote.ts
import { PrismaClient, VoteTargetType } from '@prisma/client';
import voteData from './data/vote.data';

export async function clearVotes(prisma: PrismaClient): Promise<void> {
  await prisma.vote.deleteMany();
}

export async function seedVotes(prisma: PrismaClient): Promise<{ votesCount: number }> {
  const votes = await prisma.vote.createMany({
    data: voteData.votes.map((vote) => ({
      target_id: vote.target_id,
      target_type: vote.target_type as VoteTargetType,
      user_id: vote.user_id,
      vote_type: vote.vote_type,
    })),
  });

  return {
    votesCount: votes.count,
  };
}
