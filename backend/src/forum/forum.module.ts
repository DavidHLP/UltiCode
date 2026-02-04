import { Module } from '@nestjs/common';
import { ForumService } from './forum.service';
import { ForumController } from './forum.controller';
import { VoteModule } from '../vote/vote.module';
import { AuthModule } from '../auth/auth.module';
import { BookmarkModule } from '../bookmark/bookmark.module';
import { PrismaService } from '../prisma.service';

@Module({
  imports: [VoteModule, AuthModule, BookmarkModule],
  providers: [ForumService, PrismaService],
  controllers: [ForumController],
  exports: [ForumService],
})
export class ForumModule {}
