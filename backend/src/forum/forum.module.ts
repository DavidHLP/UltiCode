import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ForumService } from './forum.service';
import { ForumController } from './forum.controller';
import { ForumPost } from './entities/post.entity';
import { ForumCommunity } from './entities/community.entity';
import { ForumUser } from './entities/user.entity';
import { ForumComment } from './entities/comment.entity';

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
