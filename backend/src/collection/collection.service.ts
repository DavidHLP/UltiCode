import {
  Injectable,
  NotFoundException,
  ForbiddenException,
} from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { CollectionTargetType, Prisma } from '@prisma/client';
import { CreateCollectionDto } from './dto/create-collection.dto';
import { UpdateCollectionDto } from './dto/update-collection.dto';
import { AddItemDto, UpdateItemDto } from './dto/collection-item.dto';

export interface CollectionSummary {
  id: string;
  name: string;
  description: string | null;
  icon: string | null;
  color: string | null;
  isDefault: boolean;
  itemCount: number;
  sortOrder: number;
  createdAt: Date;
  updatedAt: Date;
}

export interface CollectionWithItems {
  id: string;
  name: string;
  description: string | null;
  icon: string | null;
  color: string | null;
  isDefault: boolean;
  sortOrder: number;
  createdAt: Date;
  updatedAt: Date;
  items: CollectionItemDetail[];
}

export interface CollectionItemDetail {
  id: string;
  targetId: string;
  targetType: CollectionTargetType;
  sortOrder: number;
  note: string | null;
  createdAt: Date;
}

type Tx = Prisma.TransactionClient;

@Injectable()
export class CollectionService {
  constructor(private readonly prisma: PrismaService) {}

  async ensureDefaultCollection(
    userId: string,
    tx?: Tx,
  ): Promise<{ id: string; name: string }> {
    const client = tx || this.prisma;

    let defaultCollection = await client.collection.findFirst({
      where: { user_id: userId, is_default: true },
      select: { id: true, name: true },
    });

    if (!defaultCollection) {
      defaultCollection = await client.collection.create({
        data: {
          user_id: userId,
          name: 'Favorites',
          is_default: true,
        },
        select: { id: true, name: true },
      });
    }

    return defaultCollection;
  }

  async quickFavorite(
    userId: string,
    targetType: CollectionTargetType,
    targetId: string,
  ): Promise<boolean> {
    return this.prisma.$transaction(async (tx) => {
      const defaultCollection = await this.ensureDefaultCollection(userId, tx);

      const existing = await tx.collectionItem.findUnique({
        where: {
          collection_id_target_type_target_id: {
            collection_id: defaultCollection.id,
            target_type: targetType,
            target_id: targetId,
          },
        },
      });

      if (existing) {
        await tx.collectionItem.delete({ where: { id: existing.id } });
        return false;
      }

      await tx.collectionItem.create({
        data: {
          collection_id: defaultCollection.id,
          target_type: targetType,
          target_id: targetId,
        },
      });
      return true;
    });
  }

  async isInDefaultCollection(
    userId: string,
    targetType: CollectionTargetType,
    targetId: string,
  ): Promise<boolean> {
    const defaultCollection = await this.prisma.collection.findFirst({
      where: { user_id: userId, is_default: true },
      select: { id: true },
    });

    if (!defaultCollection) return false;

    const item = await this.prisma.collectionItem.findUnique({
      where: {
        collection_id_target_type_target_id: {
          collection_id: defaultCollection.id,
          target_type: targetType,
          target_id: targetId,
        },
      },
    });

    return !!item;
  }

  async getUserCollections(userId: string): Promise<CollectionSummary[]> {
    const collections = await this.prisma.collection.findMany({
      where: { user_id: userId },
      include: {
        _count: { select: { items: true } },
      },
      orderBy: [
        { is_default: 'desc' },
        { sort_order: 'asc' },
        { created_at: 'asc' },
      ],
    });

    return collections.map((c) => ({
      id: c.id,
      name: c.name,
      description: c.description,
      icon: c.icon,
      color: c.color,
      isDefault: c.is_default,
      itemCount: c._count.items,
      sortOrder: c.sort_order,
      createdAt: c.created_at,
      updatedAt: c.updated_at,
    }));
  }

  async getCollectionWithItems(
    userId: string,
    collectionId: string,
  ): Promise<CollectionWithItems> {
    const collection = await this.prisma.collection.findFirst({
      where: { id: collectionId, user_id: userId },
      include: {
        items: {
          orderBy: [{ sort_order: 'asc' }, { created_at: 'desc' }],
        },
      },
    });

    if (!collection) {
      throw new NotFoundException('Collection not found');
    }

    return {
      id: collection.id,
      name: collection.name,
      description: collection.description,
      icon: collection.icon,
      color: collection.color,
      isDefault: collection.is_default,
      sortOrder: collection.sort_order,
      createdAt: collection.created_at,
      updatedAt: collection.updated_at,
      items: collection.items.map((item) => ({
        id: item.id,
        targetId: item.target_id,
        targetType: item.target_type,
        sortOrder: item.sort_order,
        note: item.note,
        createdAt: item.created_at,
      })),
    };
  }

  async createCollection(
    userId: string,
    dto: CreateCollectionDto,
  ): Promise<CollectionSummary> {
    const maxSortOrder = await this.prisma.collection.aggregate({
      where: { user_id: userId },
      _max: { sort_order: true },
    });

    const collection = await this.prisma.collection.create({
      data: {
        user_id: userId,
        name: dto.name,
        description: dto.description,
        icon: dto.icon,
        color: dto.color,
        sort_order: (maxSortOrder._max.sort_order ?? -1) + 1,
      },
      include: {
        _count: { select: { items: true } },
      },
    });

    return {
      id: collection.id,
      name: collection.name,
      description: collection.description,
      icon: collection.icon,
      color: collection.color,
      isDefault: collection.is_default,
      itemCount: collection._count.items,
      sortOrder: collection.sort_order,
      createdAt: collection.created_at,
      updatedAt: collection.updated_at,
    };
  }

  async updateCollection(
    userId: string,
    collectionId: string,
    dto: UpdateCollectionDto,
  ): Promise<CollectionSummary> {
    const collection = await this.prisma.collection.findFirst({
      where: { id: collectionId, user_id: userId },
    });

    if (!collection) {
      throw new NotFoundException('Collection not found');
    }

    const updated = await this.prisma.collection.update({
      where: { id: collectionId },
      data: {
        name: dto.name,
        description: dto.description,
        icon: dto.icon,
        color: dto.color,
        sort_order: dto.sortOrder,
      },
      include: {
        _count: { select: { items: true } },
      },
    });

    return {
      id: updated.id,
      name: updated.name,
      description: updated.description,
      icon: updated.icon,
      color: updated.color,
      isDefault: updated.is_default,
      itemCount: updated._count.items,
      sortOrder: updated.sort_order,
      createdAt: updated.created_at,
      updatedAt: updated.updated_at,
    };
  }

  async deleteCollection(userId: string, collectionId: string): Promise<void> {
    const collection = await this.prisma.collection.findFirst({
      where: { id: collectionId, user_id: userId },
    });

    if (!collection) {
      throw new NotFoundException('Collection not found');
    }

    if (collection.is_default) {
      throw new ForbiddenException('Cannot delete the default collection');
    }

    await this.prisma.collection.delete({ where: { id: collectionId } });
  }

  async addItem(
    userId: string,
    collectionId: string,
    dto: AddItemDto,
  ): Promise<CollectionItemDetail> {
    const collection = await this.prisma.collection.findFirst({
      where: { id: collectionId, user_id: userId },
    });

    if (!collection) {
      throw new NotFoundException('Collection not found');
    }

    const maxSortOrder = await this.prisma.collectionItem.aggregate({
      where: { collection_id: collectionId },
      _max: { sort_order: true },
    });

    const item = await this.prisma.collectionItem.upsert({
      where: {
        collection_id_target_type_target_id: {
          collection_id: collectionId,
          target_type: dto.targetType,
          target_id: dto.targetId,
        },
      },
      create: {
        collection_id: collectionId,
        target_type: dto.targetType,
        target_id: dto.targetId,
        note: dto.note,
        sort_order: (maxSortOrder._max.sort_order ?? -1) + 1,
      },
      update: {
        note: dto.note,
      },
    });

    return {
      id: item.id,
      targetId: item.target_id,
      targetType: item.target_type,
      sortOrder: item.sort_order,
      note: item.note,
      createdAt: item.created_at,
    };
  }

  async removeItem(
    userId: string,
    collectionId: string,
    itemId: string,
  ): Promise<void> {
    const collection = await this.prisma.collection.findFirst({
      where: { id: collectionId, user_id: userId },
    });

    if (!collection) {
      throw new NotFoundException('Collection not found');
    }

    const item = await this.prisma.collectionItem.findFirst({
      where: { id: itemId, collection_id: collectionId },
    });

    if (!item) {
      throw new NotFoundException('Item not found in collection');
    }

    await this.prisma.collectionItem.delete({ where: { id: itemId } });
  }

  async removeItemByTarget(
    userId: string,
    collectionId: string,
    targetType: CollectionTargetType,
    targetId: string,
  ): Promise<void> {
    const collection = await this.prisma.collection.findFirst({
      where: { id: collectionId, user_id: userId },
    });

    if (!collection) {
      throw new NotFoundException('Collection not found');
    }

    await this.prisma.collectionItem.deleteMany({
      where: {
        collection_id: collectionId,
        target_type: targetType,
        target_id: targetId,
      },
    });
  }

  async updateItem(
    userId: string,
    collectionId: string,
    itemId: string,
    dto: UpdateItemDto,
  ): Promise<CollectionItemDetail> {
    const collection = await this.prisma.collection.findFirst({
      where: { id: collectionId, user_id: userId },
    });

    if (!collection) {
      throw new NotFoundException('Collection not found');
    }

    const item = await this.prisma.collectionItem.findFirst({
      where: { id: itemId, collection_id: collectionId },
    });

    if (!item) {
      throw new NotFoundException('Item not found in collection');
    }

    const updated = await this.prisma.collectionItem.update({
      where: { id: itemId },
      data: {
        note: dto.note,
        sort_order: dto.sortOrder,
      },
    });

    return {
      id: updated.id,
      targetId: updated.target_id,
      targetType: updated.target_type,
      sortOrder: updated.sort_order,
      note: updated.note,
      createdAt: updated.created_at,
    };
  }

  async getItemCollections(
    userId: string,
    targetType: CollectionTargetType,
    targetId: string,
  ): Promise<string[]> {
    const items = await this.prisma.collectionItem.findMany({
      where: {
        target_type: targetType,
        target_id: targetId,
        collection: { user_id: userId },
      },
      select: { collection_id: true },
    });

    return items.map((i) => i.collection_id);
  }

  async reorderCollections(
    userId: string,
    collectionIds: string[],
  ): Promise<void> {
    await this.prisma.$transaction(
      collectionIds.map((id, index) =>
        this.prisma.collection.updateMany({
          where: { id, user_id: userId },
          data: { sort_order: index },
        }),
      ),
    );
  }

  async getFavoriteCount(
    targetType: CollectionTargetType,
    targetId: string,
  ): Promise<number> {
    return this.prisma.collectionItem.count({
      where: {
        target_type: targetType,
        target_id: targetId,
        collection: { is_default: true },
      },
    });
  }
}
