import {
  Injectable,
  NotFoundException,
  ForbiddenException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { Prisma } from '@prisma/client';
import { CreateFolderDto } from '../dto/create-folder.dto';
import { UpdateFolderDto } from '../dto/update-folder.dto';

export interface BookmarkFolderSummary {
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

export interface BookmarkDetail {
  id: string;
  targetId: string;
  targetType: string;
  sortOrder: number;
  note: string | null;
  createdAt: Date;
  title?: string;
  metadata?: Record<string, unknown>;
}

export interface BookmarkFolderWithItems {
  id: string;
  name: string;
  description: string | null;
  icon: string | null;
  color: string | null;
  isDefault: boolean;
  sortOrder: number;
  createdAt: Date;
  updatedAt: Date;
  items: BookmarkDetail[];
}

type Tx = Prisma.TransactionClient;

@Injectable()
export class BookmarkFolderService {
  constructor(private readonly prisma: PrismaService) {}

  async ensureDefaultFolder(
    userId: string,
    tx?: Tx,
  ): Promise<{ id: string; name: string }> {
    const client = tx || this.prisma;

    let defaultFolder = await client.bookmarkFolder.findFirst({
      where: { user_id: userId, is_default: true },
      select: { id: true, name: true },
    });

    if (!defaultFolder) {
      defaultFolder = await client.bookmarkFolder.create({
        data: {
          user_id: userId,
          name: 'Favorites',
          is_default: true,
        },
        select: { id: true, name: true },
      });
    }

    return defaultFolder;
  }

  async getUserFolders(userId: string): Promise<BookmarkFolderSummary[]> {
    const folders = await this.prisma.bookmarkFolder.findMany({
      where: { user_id: userId },
      include: {
        _count: { select: { bookmarks: true } },
      },
      orderBy: [
        { is_default: 'desc' },
        { sort_order: 'asc' },
        { created_at: 'asc' },
      ],
    });

    return folders.map((f) => ({
      id: f.id,
      name: f.name,
      description: f.description,
      icon: f.icon,
      color: f.color,
      isDefault: f.is_default,
      itemCount: f._count.bookmarks,
      sortOrder: f.sort_order,
      createdAt: f.created_at,
      updatedAt: f.updated_at,
    }));
  }

  async getFolderWithBookmarks(
    userId: string,
    folderId: string,
  ): Promise<BookmarkFolderWithItems> {
    const folder = await this.prisma.bookmarkFolder.findFirst({
      where: { id: folderId, user_id: userId },
      include: {
        bookmarks: {
          orderBy: [{ sort_order: 'asc' }, { created_at: 'desc' }],
        },
      },
    });

    if (!folder) {
      throw new NotFoundException('Folder not found');
    }

    return {
      id: folder.id,
      name: folder.name,
      description: folder.description,
      icon: folder.icon,
      color: folder.color,
      isDefault: folder.is_default,
      sortOrder: folder.sort_order,
      createdAt: folder.created_at,
      updatedAt: folder.updated_at,
      items: folder.bookmarks.map((item) => ({
        id: item.id,
        targetId: item.target_id,
        targetType: item.target_type,
        sortOrder: item.sort_order,
        note: item.note,
        createdAt: item.created_at,
      })),
    };
  }

  async createFolder(
    userId: string,
    dto: CreateFolderDto,
  ): Promise<BookmarkFolderSummary> {
    const maxSortOrder = await this.prisma.bookmarkFolder.aggregate({
      where: { user_id: userId },
      _max: { sort_order: true },
    });

    const folder = await this.prisma.bookmarkFolder.create({
      data: {
        user_id: userId,
        name: dto.name,
        description: dto.description,
        icon: dto.icon,
        color: dto.color,
        sort_order: (maxSortOrder._max.sort_order ?? -1) + 1,
      },
      include: {
        _count: { select: { bookmarks: true } },
      },
    });

    return {
      id: folder.id,
      name: folder.name,
      description: folder.description,
      icon: folder.icon,
      color: folder.color,
      isDefault: folder.is_default,
      itemCount: folder._count.bookmarks,
      sortOrder: folder.sort_order,
      createdAt: folder.created_at,
      updatedAt: folder.updated_at,
    };
  }

  async updateFolder(
    userId: string,
    folderId: string,
    dto: UpdateFolderDto,
  ): Promise<BookmarkFolderSummary> {
    const folder = await this.prisma.bookmarkFolder.findFirst({
      where: { id: folderId, user_id: userId },
    });

    if (!folder) {
      throw new NotFoundException('Folder not found');
    }

    const updated = await this.prisma.bookmarkFolder.update({
      where: { id: folderId },
      data: {
        name: dto.name,
        description: dto.description,
        icon: dto.icon,
        color: dto.color,
        sort_order: dto.sortOrder,
      },
      include: {
        _count: { select: { bookmarks: true } },
      },
    });

    return {
      id: updated.id,
      name: updated.name,
      description: updated.description,
      icon: updated.icon,
      color: updated.color,
      isDefault: updated.is_default,
      itemCount: updated._count.bookmarks,
      sortOrder: updated.sort_order,
      createdAt: updated.created_at,
      updatedAt: updated.updated_at,
    };
  }

  async deleteFolder(userId: string, folderId: string): Promise<void> {
    const folder = await this.prisma.bookmarkFolder.findFirst({
      where: { id: folderId, user_id: userId },
    });

    if (!folder) {
      throw new NotFoundException('Folder not found');
    }

    if (folder.is_default) {
      throw new ForbiddenException('Cannot delete the default folder');
    }

    await this.prisma.bookmarkFolder.delete({ where: { id: folderId } });
  }

  async reorderFolders(userId: string, folderIds: string[]): Promise<void> {
    await this.prisma.$transaction(
      folderIds.map((id, index) =>
        this.prisma.bookmarkFolder.updateMany({
          where: { id, user_id: userId },
          data: { sort_order: index },
        }),
      ),
    );
  }

  async validateFolderOwnership(
    userId: string,
    folderId: string,
  ): Promise<boolean> {
    const folder = await this.prisma.bookmarkFolder.findFirst({
      where: { id: folderId, user_id: userId },
      select: { id: true },
    });

    return !!folder;
  }
}
