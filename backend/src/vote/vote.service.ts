import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { VoteDto } from './dto/vote.dto';
import { VoteTargetType, Prisma } from '@prisma/client';

type Tx = Prisma.TransactionClient;

@Injectable()
export class VoteService {
  constructor(private prisma: PrismaService) {}

  async vote(userId: string, dto: VoteDto) {
    const { targetType, targetId, voteType } = dto;

    // 1. Transaction: Handle vote logic + generic vote table update
    // We do this in a transaction to ensure data integrity
    return await this.prisma.$transaction(async (tx: Tx) => {
      // Find existing vote
      const existingVote = await tx.vote.findUnique({
        where: {
          user_id_target_type_target_id: {
            user_id: userId,
            target_type: targetType,
            target_id: targetId,
          },
        },
      });

      let finalVoteType = 0; // 0 means no vote (neutral)

      if (existingVote) {
        if (existingVote.vote_type === voteType) {
          // Toggle off: remove vote
          await tx.vote.delete({ where: { id: existingVote.id } });
          finalVoteType = 0;
        } else {
          // Change vote
          await tx.vote.update({
            where: { id: existingVote.id },
            data: { vote_type: voteType },
          });
          finalVoteType = voteType;
        }
      } else {
        // Create new vote
        await tx.vote.create({
          data: {
            user_id: userId,
            target_type: targetType,
            target_id: targetId,
            vote_type: voteType,
          },
        });
        finalVoteType = voteType;
      }

      // 2. Return current state
      // We no longer update the entity directly. The client or service querying the entity
      // must request the vote counts dynamically.
      const counts = await this.getVoteCounts(targetType, targetId, tx);
      return { ...counts, userVote: finalVoteType };
    });
  }

  async getVoteCounts(
    targetType: VoteTargetType,
    targetId: string,
    tx: Tx = this.prisma,
  ) {
    const aggregates = await tx.vote.groupBy({
      by: ['vote_type'],
      where: {
        target_type: targetType,
        target_id: targetId,
      },
      _count: true,
    });

    let likes = 0;
    let dislikes = 0;

    aggregates.forEach((agg) => {
      if (agg.vote_type === 1) likes = agg._count;
      if (agg.vote_type === -1) dislikes = agg._count;
    });

    return { likes, dislikes };
  }

  async getVoteCountsBatch(targetType: VoteTargetType, targetIds: string[]) {
    const aggregates = await this.prisma.vote.groupBy({
      by: ['target_id', 'vote_type'],
      where: {
        target_type: targetType,
        target_id: { in: targetIds },
      },
      _count: true,
    });

    const result = new Map<string, { likes: number; dislikes: number }>();

    // Initialize all to 0
    targetIds.forEach((id) => {
      result.set(id, { likes: 0, dislikes: 0 });
    });

    aggregates.forEach((agg) => {
      const stats = result.get(agg.target_id);
      if (stats) {
        if (agg.vote_type === 1) stats.likes = agg._count;
        if (agg.vote_type === -1) stats.dislikes = agg._count;
      }
    });

    return result;
  }

  async getUserVotesBatch(
    userId: string,
    targetType: VoteTargetType,
    targetIds: string[],
  ): Promise<Map<string, number>> {
    const votes = await this.prisma.vote.findMany({
      where: {
        user_id: userId,
        target_type: targetType,
        target_id: { in: targetIds },
      },
      select: {
        target_id: true,
        vote_type: true,
      },
    });

    const result = new Map<string, number>();
    votes.forEach((v) => {
      result.set(v.target_id, v.vote_type);
    });
    return result;
  }
}
