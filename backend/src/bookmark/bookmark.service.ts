import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { BookmarkType } from '@prisma/client';
import { CreateFolderDto } from './dto/create-folder.dto';
import { UpdateFolderDto } from './dto/update-folder.dto';
import { AddBookmarkDto, UpdateBookmarkDto } from './dto/bookmark-item.dto';
import { BookmarkFolderService } from './services/bookmark-folder.service';
import { BookmarkCrudService } from './services/bookmark-crud.service';
import { BookmarkQueryService } from './services/bookmark-query.service';

export type {
  BookmarkFolderSummary,
  BookmarkDetail,
  BookmarkFolderWithItems,
} from './services/bookmark-folder.service';

@Injectable()
export class BookmarkService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly folderService: BookmarkFolderService,
    private readonly crudService: BookmarkCrudService,
    private readonly queryService: BookmarkQueryService,
  ) {}

  async ensureDefaultFolder(userId: string) {
    return this.folderService.ensureDefaultFolder(userId);
  }

  async quickFavorite(
    userId: string,
    targetType: BookmarkType,
    targetId: string,
  ): Promise<boolean> {
    return this.crudService.quickFavorite(userId, targetType, targetId);
  }

  async isInDefaultFolder(
    userId: string,
    targetType: BookmarkType,
    targetId: string,
  ): Promise<boolean> {
    return this.crudService.isInDefaultFolder(userId, targetType, targetId);
  }

  async getUserFolders(userId: string) {
    return this.folderService.getUserFolders(userId);
  }

  async getFolderWithBookmarks(userId: string, folderId: string) {
    return this.queryService.getFolderWithHydratedItems(userId, folderId);
  }

  async createFolder(userId: string, dto: CreateFolderDto) {
    return this.folderService.createFolder(userId, dto);
  }

  async updateFolder(userId: string, folderId: string, dto: UpdateFolderDto) {
    return this.folderService.updateFolder(userId, folderId, dto);
  }

  async deleteFolder(userId: string, folderId: string): Promise<void> {
    return this.folderService.deleteFolder(userId, folderId);
  }

  async addBookmark(userId: string, folderId: string, dto: AddBookmarkDto) {
    return this.crudService.addBookmark(userId, folderId, dto);
  }

  async removeBookmark(
    userId: string,
    folderId: string,
    bookmarkId: string,
  ): Promise<void> {
    return this.crudService.removeBookmark(userId, folderId, bookmarkId);
  }

  async removeBookmarkByTarget(
    userId: string,
    folderId: string,
    targetType: BookmarkType,
    targetId: string,
  ): Promise<void> {
    return this.crudService.removeBookmarkByTarget(
      userId,
      folderId,
      targetType,
      targetId,
    );
  }

  async updateBookmark(
    userId: string,
    folderId: string,
    bookmarkId: string,
    dto: UpdateBookmarkDto,
  ) {
    return this.crudService.updateBookmark(userId, folderId, bookmarkId, dto);
  }

  async getBookmarkFolders(
    userId: string,
    targetType: BookmarkType,
    targetId: string,
  ): Promise<string[]> {
    return this.queryService.getBookmarkFolders(userId, targetType, targetId);
  }

  async getBookmarkStatusBatch(
    userId: string,
    targetType: BookmarkType,
    targetIds: string[],
  ) {
    return this.queryService.getBookmarkStatusBatch(
      userId,
      targetType,
      targetIds,
    );
  }

  async reorderFolders(userId: string, folderIds: string[]): Promise<void> {
    return this.folderService.reorderFolders(userId, folderIds);
  }

  async getFavoriteCount(
    targetType: BookmarkType,
    targetId: string,
  ): Promise<number> {
    return this.queryService.getFavoriteCount(targetType, targetId);
  }

  async getFavoriteCountsBatch(targetType: BookmarkType, targetIds: string[]) {
    return this.queryService.getFavoriteCountsBatch(targetType, targetIds);
  }
}
