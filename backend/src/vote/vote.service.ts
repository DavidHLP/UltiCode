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

      // 2. Aggregate Update
      // Recalculate totals for this target
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

      // 3. Update Target Entity
      // Dispatch based on targetType to update the specific table
      await this.updateEntityStats(tx, targetType, targetId, likes, dislikes);

      return { likes, dislikes, userVote: finalVoteType };
    });
  }

  private async updateEntityStats(
    tx: Tx,
    targetType: VoteTargetType,
    targetId: string,
    likes: number,
    dislikes: number,
  ) {
    switch (targetType) {
      case VoteTargetType.SOLUTION:
        await tx.solution.update({
          where: { id: targetId },
          data: { likes, dislikes },
        });
        break;
      case VoteTargetType.SOLUTION_COMMENT:
        await tx.solutionComment.update({
          where: { id: targetId },
          data: { likes, dislikes },
        });
        break;
      case VoteTargetType.FORUM_POST:
        await tx.forumPost.update({
          where: { id: targetId },
          data: { likes, dislikes },
        });
        break;
      case VoteTargetType.FORUM_COMMENT:
        await tx.forumComment.update({
          where: { id: targetId },
          data: { likes, dislikes },
        });
        break;
      default:
        console.warn(
          `No entity stats update logic for targetType: ${targetType as any}`,
        );
    }
  }
}
