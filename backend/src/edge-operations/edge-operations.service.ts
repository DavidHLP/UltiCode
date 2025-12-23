import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import {
  EdgeOperationTargetType,
  EdgeOperationType,
  Prisma,
} from '@prisma/client';
import { VoteService } from '../vote/vote.service';
import { EdgeOperationDto } from './dto/edge-operation.dto';

type Tx = Prisma.TransactionClient;

export interface EdgeOperationResponse {
  likes: number;
  dislikes: number;
  favorites: number;
  userOperation: EdgeOperationType | null;
}

@Injectable()
export class EdgeOperationsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly voteService: VoteService,
  ) {}

  async operate(
    userId: string,
    dto: EdgeOperationDto,
  ): Promise<EdgeOperationResponse> {
    const { operationType, targetType, targetId } = dto;

    if (
      operationType === EdgeOperationType.VOTE_UP ||
      operationType === EdgeOperationType.VOTE_DOWN
    ) {
      const voteType = operationType === EdgeOperationType.VOTE_UP ? 1 : -1;
      const voteResult = await this.voteService.vote(userId, {
        targetType,
        targetId,
        voteType,
      });

      const favorites = await this.getOperationCount(
        targetType,
        targetId,
        EdgeOperationType.FAVORITE,
      );

      const userOperation =
        voteResult.userVote === 1
          ? EdgeOperationType.VOTE_UP
          : voteResult.userVote === -1
            ? EdgeOperationType.VOTE_DOWN
            : null;

      return {
        likes: voteResult.likes,
        dislikes: voteResult.dislikes,
        favorites,
        userOperation,
      };
    }

    const userOperation = await this.toggleOperation(
      userId,
      targetType,
      targetId,
      operationType,
    );

    const voteCounts = await this.voteService.getVoteCounts(
      targetType,
      targetId,
    );
    const favorites = await this.getOperationCount(
      targetType,
      targetId,
      EdgeOperationType.FAVORITE,
    );

    return {
      likes: voteCounts.likes,
      dislikes: voteCounts.dislikes,
      favorites,
      userOperation,
    };
  }

  private async toggleOperation(
    userId: string,
    targetType: EdgeOperationTargetType,
    targetId: string,
    operationType: EdgeOperationType,
  ): Promise<EdgeOperationType | null> {
    return await this.prisma.$transaction(async (tx: Tx) => {
      const existing = await tx.edgeOperation.findUnique({
        where: {
          operator_id_operation_type_target_type_target_id: {
            operator_id: userId,
            operation_type: operationType,
            target_type: targetType,
            target_id: targetId,
          },
        },
      });

      if (existing) {
        await tx.edgeOperation.delete({ where: { id: existing.id } });
        return null;
      }

      await tx.edgeOperation.create({
        data: {
          operator_id: userId,
          target_type: targetType,
          target_id: targetId,
          operation_type: operationType,
        },
      });

      return operationType;
    });
  }

  private async getOperationCount(
    targetType: EdgeOperationTargetType,
    targetId: string,
    operationType: EdgeOperationType,
  ): Promise<number> {
    return this.prisma.edgeOperation.count({
      where: {
        target_type: targetType,
        target_id: targetId,
        operation_type: operationType,
      },
    });
  }
}
