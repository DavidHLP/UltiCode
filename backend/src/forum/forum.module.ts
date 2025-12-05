import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ForumService } from './forum.service';
import { ForumController } from './forum.controller';
import { ForumPost } from './forum-post.entity';
import { ForumCommunity } from './forum-community.entity';
import { ForumUser } from './forum-user.entity';
import { ForumComment } from './forum-comment.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      ForumPost,
      ForumCommunity,
      ForumUser,
      ForumComment,
    ]),
  ],
  providers: [ForumService],
  controllers: [ForumController],
  exports: [ForumService],
})
export class ForumModule {}
