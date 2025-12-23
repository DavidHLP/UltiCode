import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { VoteDto } from './dto/vote.dto';
import {
  EdgeOperationTargetType,
  EdgeOperationType,
  Prisma,
} from '@prisma/client';

type Tx = Prisma.TransactionClient;

@Injectable()
export class VoteService {
  constructor(private prisma: PrismaService) {}

  async vote(userId: string, dto: VoteDto) {
    const { targetType, targetId, voteType } = dto;
    const operationType =
      voteType === 1 ? EdgeOperationType.VOTE_UP : EdgeOperationType.VOTE_DOWN;

    // 1. Transaction: Handle vote logic + generic vote table update
    // We do this in a transaction to ensure data integrity
    return await this.prisma.$transaction(async (tx: Tx) => {
      // Find existing vote operations (up/down)
      const existingVote = await tx.edgeOperation.findFirst({
        where: {
          operator_id: userId,
          target_type: targetType,
          target_id: targetId,
          operation_type: {
            in: [EdgeOperationType.VOTE_UP, EdgeOperationType.VOTE_DOWN],
          },
        },
      });

      let finalVoteType = 0; // 0 means no vote (neutral)

      if (existingVote) {
        if (existingVote.operation_type === operationType) {
          // Toggle off: remove vote
          await tx.edgeOperation.delete({ where: { id: existingVote.id } });
          finalVoteType = 0;
        } else {
          // Change vote
          await tx.edgeOperation.update({
            where: { id: existingVote.id },
            data: { operation_type: operationType },
          });
          finalVoteType = voteType;
        }
      } else {
        // Create new vote
        await tx.edgeOperation.create({
          data: {
            operator_id: userId,
            target_type: targetType,
            target_id: targetId,
            operation_type: operationType,
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
    targetType: EdgeOperationTargetType,
    targetId: string,
    tx: Tx = this.prisma,
  ) {
    const aggregates = await tx.edgeOperation.groupBy({
      by: ['operation_type'],
      where: {
        target_type: targetType,
        target_id: targetId,
        operation_type: {
          in: [EdgeOperationType.VOTE_UP, EdgeOperationType.VOTE_DOWN],
        },
      },
      _count: true,
    });

    let likes = 0;
    let dislikes = 0;

    aggregates.forEach((agg) => {
      if (agg.operation_type === EdgeOperationType.VOTE_UP) likes = agg._count;
      if (agg.operation_type === EdgeOperationType.VOTE_DOWN)
        dislikes = agg._count;
    });

    return { likes, dislikes };
  }

  async getVoteCountsBatch(
    targetType: EdgeOperationTargetType,
    targetIds: string[],
  ) {
    const aggregates = await this.prisma.edgeOperation.groupBy({
      by: ['target_id', 'operation_type'],
      where: {
        target_type: targetType,
        target_id: { in: targetIds },
        operation_type: {
          in: [EdgeOperationType.VOTE_UP, EdgeOperationType.VOTE_DOWN],
        },
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
        if (agg.operation_type === EdgeOperationType.VOTE_UP)
          stats.likes = agg._count;
        if (agg.operation_type === EdgeOperationType.VOTE_DOWN)
          stats.dislikes = agg._count;
      }
    });

    return result;
  }

  async getUserVotesBatch(
    userId: string,
    targetType: EdgeOperationTargetType,
    targetIds: string[],
  ): Promise<Map<string, number>> {
    const votes = await this.prisma.edgeOperation.findMany({
      where: {
        operator_id: userId,
        target_type: targetType,
        target_id: { in: targetIds },
        operation_type: {
          in: [EdgeOperationType.VOTE_UP, EdgeOperationType.VOTE_DOWN],
        },
      },
      select: {
        target_id: true,
        operation_type: true,
      },
    });

    const result = new Map<string, number>();
    votes.forEach((v) => {
      result.set(
        v.target_id,
        v.operation_type === EdgeOperationType.VOTE_UP ? 1 : -1,
      );
    });
    return result;
  }
}
