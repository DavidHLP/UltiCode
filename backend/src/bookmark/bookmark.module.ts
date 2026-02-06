import { Module } from '@nestjs/common';
import { BookmarkController } from './bookmark.controller';
import { BookmarkService } from './bookmark.service';
import { PrismaService } from '../prisma.service';
import { BookmarkFolderService } from './services/bookmark-folder.service';
import { BookmarkCrudService } from './services/bookmark-crud.service';
import { BookmarkQueryService } from './services/bookmark-query.service';

@Module({
  controllers: [BookmarkController],
  providers: [
    PrismaService,
    BookmarkFolderService,
    BookmarkCrudService,
    BookmarkQueryService,
    BookmarkService,
  ],
  exports: [BookmarkService],
})
export class BookmarkModule {}
