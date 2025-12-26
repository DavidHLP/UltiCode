import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import {
  EdgeOperationTargetType,
  EdgeOperationType,
  CollectionTargetType,
  Prisma,
} from '@prisma/client';
import { VoteService } from '../vote/vote.service';
import { CollectionService } from '../collection/collection.service';
import { EdgeOperationDto } from './dto/edge-operation.dto';

type Tx = Prisma.TransactionClient;

export interface EdgeOperationViewerState {
  vote: 1 | 0 | -1;
  isFavorite: boolean;
  collections?: string[];
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
    private readonly collectionService: CollectionService,
  ) {}

  private mapToCollectionTargetType(
    edgeTargetType: EdgeOperationTargetType,
  ): CollectionTargetType | null {
    const mapping: Record<
      EdgeOperationTargetType,
      CollectionTargetType | null
    > = {
      [EdgeOperationTargetType.PROBLEM]: CollectionTargetType.PROBLEM,
      [EdgeOperationTargetType.SOLUTION]: CollectionTargetType.SOLUTION,
      [EdgeOperationTargetType.FORUM_POST]: CollectionTargetType.FORUM_POST,
      [EdgeOperationTargetType.PROBLEM_LIST]: CollectionTargetType.PROBLEM_LIST,
      [EdgeOperationTargetType.SOLUTION_COMMENT]: null,
      [EdgeOperationTargetType.FORUM_COMMENT]: null,
    };
    return mapping[edgeTargetType];
  }

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
      const isFavorite = await this.getUserFavorite(
        userId,
        targetType,
        targetId,
      );

      return {
        likes: voteResult.likes,
        dislikes: voteResult.dislikes,
        favorites,
        viewer: {
          vote: voteResult.userVote as 1 | 0 | -1,
          isFavorite,
        },
      };
    }

    if (operationType === EdgeOperationType.FAVORITE) {
      const collectionTargetType = this.mapToCollectionTargetType(targetType);
      let isFavorite = false;
      let collections: string[] = [];

      if (collectionTargetType) {
        isFavorite = await this.collectionService.quickFavorite(
          userId,
          collectionTargetType,
          targetId,
        );
        collections = await this.collectionService.getItemCollections(
          userId,
          collectionTargetType,
          targetId,
        );
      } else {
        isFavorite = await this.toggleOperation(
          userId,
          targetType,
          targetId,
          operationType,
        );
      }

      const voteCounts = await this.voteService.getVoteCounts(
        targetType,
        targetId,
      );
      const favorites = collectionTargetType
        ? await this.collectionService.getFavoriteCount(
            collectionTargetType,
            targetId,
          )
        : await this.getOperationCount(
            targetType,
            targetId,
            EdgeOperationType.FAVORITE,
          );
      const userVote = await this.voteService.getUserVote(
        userId,
        targetType,
        targetId,
      );

      return {
        likes: voteCounts.likes,
        dislikes: voteCounts.dislikes,
        favorites,
        viewer: {
          vote: userVote as 1 | 0 | -1,
          isFavorite,
          collections,
        },
      };
    }

    await this.toggleOperation(userId, targetType, targetId, operationType);

    const voteCounts = await this.voteService.getVoteCounts(
      targetType,
      targetId,
    );
    const favorites = await this.getOperationCount(
      targetType,
      targetId,
      EdgeOperationType.FAVORITE,
    );
    const userVote = await this.voteService.getUserVote(
      userId,
      targetType,
      targetId,
    );
    const isFavorite = await this.getUserFavorite(userId, targetType, targetId);

    return {
      likes: voteCounts.likes,
      dislikes: voteCounts.dislikes,
      favorites,
      viewer: {
        vote: userVote as 1 | 0 | -1,
        isFavorite,
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

  private async getUserFavorite(
    userId: string,
    targetType: EdgeOperationTargetType,
    targetId: string,
  ): Promise<boolean> {
    const favorite = await this.prisma.edgeOperation.findUnique({
      where: {
        operator_id_operation_type_target_type_target_id: {
          operator_id: userId,
          operation_type: EdgeOperationType.FAVORITE,
          target_type: targetType,
          target_id: targetId,
        },
      },
    });
    return !!favorite;
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

  async getInteractions(
    targetType: EdgeOperationTargetType,
    targetId: string,
    userId?: string,
  ): Promise<EdgeOperationResponse> {
    const voteCounts = await this.voteService.getVoteCounts(
      targetType,
      targetId,
    );

    const collectionTargetType = this.mapToCollectionTargetType(targetType);
    const favorites = collectionTargetType
      ? await this.collectionService.getFavoriteCount(
          collectionTargetType,
          targetId,
        )
      : await this.getOperationCount(
          targetType,
          targetId,
          EdgeOperationType.FAVORITE,
        );

    let viewerVote: 1 | 0 | -1 = 0;
    let isFavorite = false;
    let collections: string[] = [];

    if (userId) {
      viewerVote = (await this.voteService.getUserVote(
        userId,
        targetType,
        targetId,
      )) as 1 | 0 | -1;

      if (collectionTargetType) {
        isFavorite = await this.collectionService.isInDefaultCollection(
          userId,
          collectionTargetType,
          targetId,
        );
        collections = await this.collectionService.getItemCollections(
          userId,
          collectionTargetType,
          targetId,
        );
      } else {
        isFavorite = await this.getUserFavorite(userId, targetType, targetId);
      }
    }

    return {
      likes: voteCounts.likes,
      dislikes: voteCounts.dislikes,
      favorites,
      viewer: {
        vote: viewerVote,
        isFavorite,
        collections,
      },
    };
  }
}
