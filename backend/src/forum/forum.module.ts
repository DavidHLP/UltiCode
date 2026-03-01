import { Module } from '@nestjs/common';
import { ForumService } from './forum.service';
import { ForumController } from './forum.controller';
import { VoteModule } from '../vote/vote.module';
import { AuthModule } from '../auth/auth.module';
import { BookmarkModule } from '../bookmark/bookmark.module';
import { NotificationModule } from '../notification/notification.module';
import { PrismaService } from '../prisma.service';

// 子服务
import { ForumModerationService } from './services/forum-moderation.service';
import { ForumCommentService } from './services/forum-comment.service';
import { ForumPostService } from './services/forum-post.service';
import { ForumCommunityService } from './services/forum-community.service';

@Module({
  imports: [VoteModule, AuthModule, BookmarkModule, NotificationModule],
  providers: [
    PrismaService,
    // 基础服务（无依赖或依赖少）
    ForumModerationService,
    // 中层服务（依赖基础服务）
    ForumCommentService,
    ForumCommunityService,
    ForumPostService,
    // 主服务（最后注册，依赖所有子服务）
    ForumService,
  ],
  controllers: [ForumController],
  exports: [ForumService],
})
export class ForumModule {}
