import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { BookmarkType } from '@prisma/client';
import { AddBookmarkDto, UpdateBookmarkDto } from '../dto/bookmark-item.dto';
import {
  BookmarkFolderService,
  BookmarkDetail,
} from './bookmark-folder.service';

@Injectable()
export class BookmarkCrudService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly folderService: BookmarkFolderService,
  ) {}

  async quickFavorite(
    userId: string,
    targetType: BookmarkType,
    targetId: string,
  ): Promise<boolean> {
    return this.prisma.$transaction(async (tx) => {
      const defaultFolder = await this.folderService.ensureDefaultFolder(
        userId,
        tx,
      );

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

  async addBookmark(
    userId: string,
    folderId: string,
    dto: AddBookmarkDto,
  ): Promise<BookmarkDetail> {
    const isValid = await this.folderService.validateFolderOwnership(
      userId,
      folderId,
    );

    if (!isValid) {
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
    const isValid = await this.folderService.validateFolderOwnership(
      userId,
      folderId,
    );

    if (!isValid) {
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
    const isValid = await this.folderService.validateFolderOwnership(
      userId,
      folderId,
    );

    if (!isValid) {
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
    const isValid = await this.folderService.validateFolderOwnership(
      userId,
      folderId,
    );

    if (!isValid) {
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
}
