import {
  Controller,
  Get,
  Post,
  Patch,
  Delete,
  Body,
  Param,
  UseGuards,
  Req,
} from '@nestjs/common';
import type { Request } from 'express';
import { BookmarkService } from './bookmark.service';
import { AuthGuard } from '../auth/auth.guard';
import { CreateFolderDto } from './dto/create-folder.dto';
import { UpdateFolderDto } from './dto/update-folder.dto';
import {
  AddBookmarkDto,
  UpdateBookmarkDto,
  ReorderFoldersDto,
} from './dto/bookmark-item.dto';
import { QuickFavoriteDto } from './dto/quick-favorite.dto';
import { BookmarkType } from '@prisma/client';

interface AuthenticatedRequest extends Request {
  user: { id: string };
}

@Controller('bookmarks')
@UseGuards(AuthGuard)
export class BookmarkController {
  constructor(private readonly bookmarkService: BookmarkService) {}

  @Post('quick')
  async quickFavorite(
    @Req() req: AuthenticatedRequest,
    @Body() dto: QuickFavoriteDto,
  ) {
    const isSaved = await this.bookmarkService.quickFavorite(
      req.user.id,
      dto.targetType,
      dto.targetId,
    );
    return { isSaved };
  }

  @Get('folders')
  async getUserFolders(@Req() req: AuthenticatedRequest) {
    return this.bookmarkService.getUserFolders(req.user.id);
  }

  @Get('folders/:id')
  async getFolder(@Req() req: AuthenticatedRequest, @Param('id') id: string) {
    return this.bookmarkService.getFolderWithBookmarks(req.user.id, id);
  }

  @Post('folders')
  async createFolder(
    @Req() req: AuthenticatedRequest,
    @Body() dto: CreateFolderDto,
  ) {
    return this.bookmarkService.createFolder(req.user.id, dto);
  }

  @Patch('folders/:id')
  async updateFolder(
    @Req() req: AuthenticatedRequest,
    @Param('id') id: string,
    @Body() dto: UpdateFolderDto,
  ) {
    return this.bookmarkService.updateFolder(req.user.id, id, dto);
  }

  @Delete('folders/:id')
  async deleteFolder(
    @Req() req: AuthenticatedRequest,
    @Param('id') id: string,
  ) {
    await this.bookmarkService.deleteFolder(req.user.id, id);
    return { success: true };
  }

  @Post('folders/:id/items')
  async addBookmark(
    @Req() req: AuthenticatedRequest,
    @Param('id') folderId: string,
    @Body() dto: AddBookmarkDto,
  ) {
    return this.bookmarkService.addBookmark(req.user.id, folderId, dto);
  }

  @Delete('folders/:id/items/:bookmarkId')
  async removeBookmark(
    @Req() req: AuthenticatedRequest,
    @Param('id') folderId: string,
    @Param('bookmarkId') bookmarkId: string,
  ) {
    await this.bookmarkService.removeBookmark(
      req.user.id,
      folderId,
      bookmarkId,
    );
    return { success: true };
  }

  @Delete('folders/:id/items/target/:targetType/:targetId')
  async removeBookmarkByTarget(
    @Req() req: AuthenticatedRequest,
    @Param('id') folderId: string,
    @Param('targetType') targetType: BookmarkType,
    @Param('targetId') targetId: string,
  ) {
    await this.bookmarkService.removeBookmarkByTarget(
      req.user.id,
      folderId,
      targetType,
      targetId,
    );
    return { success: true };
  }

  @Patch('folders/:id/items/:bookmarkId')
  async updateBookmark(
    @Req() req: AuthenticatedRequest,
    @Param('id') folderId: string,
    @Param('bookmarkId') bookmarkId: string,
    @Body() dto: UpdateBookmarkDto,
  ) {
    return this.bookmarkService.updateBookmark(
      req.user.id,
      folderId,
      bookmarkId,
      dto,
    );
  }

  @Get('item/:targetType/:targetId')
  async getBookmarkFolders(
    @Req() req: AuthenticatedRequest,
    @Param('targetType') targetType: BookmarkType,
    @Param('targetId') targetId: string,
  ) {
    return this.bookmarkService.getBookmarkFolders(
      req.user.id,
      targetType,
      targetId,
    );
  }

  @Post('folders/reorder')
  async reorderFolders(
    @Req() req: AuthenticatedRequest,
    @Body() dto: ReorderFoldersDto,
  ) {
    await this.bookmarkService.reorderFolders(req.user.id, dto.folderIds);
    return { success: true };
  }
}
