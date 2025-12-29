import {
  Injectable,
  NotFoundException,
  ForbiddenException,
} from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { BookmarkType, Prisma } from '@prisma/client';
import { CreateFolderDto } from './dto/create-folder.dto';
import { UpdateFolderDto } from './dto/update-folder.dto';
import { AddBookmarkDto, UpdateBookmarkDto } from './dto/bookmark-item.dto';

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

export interface BookmarkDetail {
  id: string;
  targetId: string;
  targetType: BookmarkType;
  sortOrder: number;
  note: string | null;
  createdAt: Date;
  title?: string;
  metadata?: Record<string, any>;
}

type Tx = Prisma.TransactionClient;

@Injectable()
export class BookmarkService {
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

  async quickFavorite(
    userId: string,
    targetType: BookmarkType,
    targetId: string,
  ): Promise<boolean> {
    return this.prisma.$transaction(async (tx) => {
      const defaultFolder = await this.ensureDefaultFolder(userId, tx);

      const existing = await tx.bookmark.findUnique({
        where: {
          folder_id_target_type_target_id: {
            folder_id: defaultFolder.id,
            target_type: targetType,
            target_id: targetId,
          },
        },
      });

      if (existing) {
        await tx.bookmark.delete({ where: { id: existing.id } });
        return false;
      }

      await tx.bookmark.create({
        data: {
          folder_id: defaultFolder.id,
          target_type: targetType,
          target_id: targetId,
        },
      });
      return true;
    });
  }

  async isInDefaultFolder(
    userId: string,
    targetType: BookmarkType,
    targetId: string,
  ): Promise<boolean> {
    const defaultFolder = await this.prisma.bookmarkFolder.findFirst({
      where: { user_id: userId, is_default: true },
      select: { id: true },
    });

    if (!defaultFolder) return false;

    const item = await this.prisma.bookmark.findUnique({
      where: {
        folder_id_target_type_target_id: {
          folder_id: defaultFolder.id,
          target_type: targetType,
          target_id: targetId,
        },
      },
    });

    return !!item;
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

    // Hydrate items
    const forumPostIds = folder.bookmarks
      .filter((b) => b.target_type === BookmarkType.FORUM_POST)
      .map((b) => b.target_id);

    let forumPostsMap = new Map<string, any>();
    if (forumPostIds.length > 0) {
      const posts = await this.prisma.forumPost.findMany({
        where: { id: { in: forumPostIds } },
        select: {
          id: true,
          title: true,
          community: { select: { name: true, slug: true } },
          author: { select: { username: true, avatar: true } },
        },
      });
      forumPostsMap = new Map(posts.map((p) => [p.id, p]));
    }

    const items: BookmarkDetail[] = folder.bookmarks.map((item) => {
      let title = undefined;
      let metadata = undefined;

      if (item.target_type === BookmarkType.FORUM_POST) {
        const post = forumPostsMap.get(item.target_id);
        if (post) {
          title = post.title;
          metadata = {
            communityName: post.community.name,
            communitySlug: post.community.slug,
            authorName: post.author.username,
            authorAvatar: post.author.avatar,
          };
        }
      }

      return {
        id: item.id,
        targetId: item.target_id,
        targetType: item.target_type,
        sortOrder: item.sort_order,
        note: item.note,
        createdAt: item.created_at,
        title,
        metadata,
      };
    });

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
      items,
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

  async addBookmark(
    userId: string,
    folderId: string,
    dto: AddBookmarkDto,
  ): Promise<BookmarkDetail> {
    const folder = await this.prisma.bookmarkFolder.findFirst({
      where: { id: folderId, user_id: userId },
    });

    if (!folder) {
      throw new NotFoundException('Folder not found');
    }

    const maxSortOrder = await this.prisma.bookmark.aggregate({
      where: { folder_id: folderId },
      _max: { sort_order: true },
    });

    const item = await this.prisma.bookmark.upsert({
      where: {
        folder_id_target_type_target_id: {
          folder_id: folderId,
          target_type: dto.targetType,
          target_id: dto.targetId,
        },
      },
      create: {
        folder_id: folderId,
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

  async removeBookmark(
    userId: string,
    folderId: string,
    bookmarkId: string,
  ): Promise<void> {
    const folder = await this.prisma.bookmarkFolder.findFirst({
      where: { id: folderId, user_id: userId },
    });

    if (!folder) {
      throw new NotFoundException('Folder not found');
    }

    const item = await this.prisma.bookmark.findFirst({
      where: { id: bookmarkId, folder_id: folderId },
    });

    if (!item) {
      throw new NotFoundException('Bookmark not found in folder');
    }

    await this.prisma.bookmark.delete({ where: { id: bookmarkId } });
  }

  async removeBookmarkByTarget(
    userId: string,
    folderId: string,
    targetType: BookmarkType,
    targetId: string,
  ): Promise<void> {
    const folder = await this.prisma.bookmarkFolder.findFirst({
      where: { id: folderId, user_id: userId },
    });

    if (!folder) {
      throw new NotFoundException('Folder not found');
    }

    await this.prisma.bookmark.deleteMany({
      where: {
        folder_id: folderId,
        target_type: targetType,
        target_id: targetId,
      },
    });
  }

  async updateBookmark(
    userId: string,
    folderId: string,
    bookmarkId: string,
    dto: UpdateBookmarkDto,
  ): Promise<BookmarkDetail> {
    const folder = await this.prisma.bookmarkFolder.findFirst({
      where: { id: folderId, user_id: userId },
    });

    if (!folder) {
      throw new NotFoundException('Folder not found');
    }

    const item = await this.prisma.bookmark.findFirst({
      where: { id: bookmarkId, folder_id: folderId },
    });

    if (!item) {
      throw new NotFoundException('Bookmark not found in folder');
    }

    const updated = await this.prisma.bookmark.update({
      where: { id: bookmarkId },
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

  async getBookmarkFolders(
    userId: string,
    targetType: BookmarkType,
    targetId: string,
  ): Promise<string[]> {
    const items = await this.prisma.bookmark.findMany({
      where: {
        target_type: targetType,
        target_id: targetId,
        folder: { user_id: userId },
      },
      select: { folder_id: true },
    });

    return items.map((i) => i.folder_id);
  }

  async getBookmarkStatusBatch(
    userId: string,
    targetType: BookmarkType,
    targetIds: string[],
  ): Promise<Map<string, boolean>> {
    const defaultFolder = await this.prisma.bookmarkFolder.findFirst({
      where: { user_id: userId, is_default: true },
      select: { id: true },
    });

    if (!defaultFolder) return new Map();

    const items = await this.prisma.bookmark.findMany({
      where: {
        folder_id: defaultFolder.id,
        target_type: targetType,
        target_id: { in: targetIds },
      },
      select: { target_id: true },
    });

    const result = new Map<string, boolean>();
    items.forEach((item) => result.set(item.target_id, true));
    return result;
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

  async getFavoriteCount(
    targetType: BookmarkType,
    targetId: string,
  ): Promise<number> {
    return this.prisma.bookmark.count({
      where: {
        target_type: targetType,
        target_id: targetId,
        folder: { is_default: true },
      },
    });
  }
}