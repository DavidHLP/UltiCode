import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import {
  EdgeOperationTargetType,
  EdgeOperationType,
  Prisma,
  BookmarkType,
} from '@prisma/client';
import { VoteService } from '../vote/vote.service';
import { EdgeOperationDto } from './dto/edge-operation.dto';

type Tx = Prisma.TransactionClient;

export interface EdgeOperationViewerState {
  vote: 1 | 0 | -1;
}

export interface EdgeOperationResponse {
  likes: number;
  dislikes: number;
  favorites: number;
  viewer: EdgeOperationViewerState;
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

    // Handle voting operations
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

      return {
        likes: voteResult.likes,
        dislikes: voteResult.dislikes,
        favorites: await this.getFavoritesCount(targetType, targetId),
        viewer: {
          vote: voteResult.userVote as 1 | 0 | -1,
        },
      };
    }

    // Handle other operations (ANALYZE, etc.)
    await this.toggleOperation(userId, targetType, targetId, operationType);

    const voteCounts = await this.voteService.getVoteCounts(
      targetType,
      targetId,
    );
    const userVote = await this.voteService.getUserVote(
      userId,
      targetType,
      targetId,
    );

    return {
      likes: voteCounts.likes,
      dislikes: voteCounts.dislikes,
      favorites: await this.getFavoritesCount(targetType, targetId),
      viewer: {
        vote: userVote as 1 | 0 | -1,
      },
    };
  }

  private async toggleOperation(
    userId: string,
    targetType: EdgeOperationTargetType,
    targetId: string,
    operationType: EdgeOperationType,
  ): Promise<boolean> {
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
        return false;
      }

      await tx.edgeOperation.create({
        data: {
          operator_id: userId,
          target_type: targetType,
          target_id: targetId,
          operation_type: operationType,
        },
      });

      return true;
    });
  }

  private async getFavoritesCount(
    targetType: EdgeOperationTargetType,
    targetId: string,
  ): Promise<number> {
    if (targetType === EdgeOperationTargetType.PROBLEM) {
      const problemIdBigInt = BigInt(targetId);

      const [usersWithBookmarks, usersWithLists] = await Promise.all([
        this.prisma.bookmarkFolder.findMany({
          where: {
            bookmarks: {
              some: { target_type: BookmarkType.PROBLEM, target_id: targetId },
            },
          },
          select: { user_id: true },
          distinct: ['user_id'],
        }),
        this.prisma.problemList.findMany({
          where: {
            problemRelations: { some: { problem_id: problemIdBigInt } },
          },
          select: { author_id: true },
          distinct: ['author_id'],
        }),
      ]);

      const uniqueUsers = new Set([
        ...usersWithBookmarks.map((u) => u.user_id),
        ...usersWithLists.map((u) => u.author_id),
      ]);

      return uniqueUsers.size;
    }
    // Implement for other types if needed, return 0 for now
    return 0;
  }

  async getInteractions(
    targetType: EdgeOperationTargetType,
    targetId: string,
    userId?: string,
  ): Promise<EdgeOperationResponse> {
    const voteCounts = await this.voteService.getVoteCounts(
      targetType,
      targetId,
    );

    let viewerVote: 1 | 0 | -1 = 0;

    if (userId) {
      viewerVote = (await this.voteService.getUserVote(
        userId,
        targetType,
        targetId,
      )) as 1 | 0 | -1;
    }

    return {
      likes: voteCounts.likes,
      dislikes: voteCounts.dislikes,
      favorites: await this.getFavoritesCount(targetType, targetId),
      viewer: {
        vote: viewerVote,
      },
    };
  }
}
