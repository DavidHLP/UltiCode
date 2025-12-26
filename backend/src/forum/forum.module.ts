import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ForumService } from './forum.service';
import { ForumController } from './forum.controller';
import { ForumPost } from './entities/post.entity';
import { ForumCommunity } from './entities/community.entity';
import { ForumComment } from './entities/comment.entity';
import { ForumTag } from './entities/tag.entity';
import { ForumPostTagRelation } from './entities/post-tag-relation.entity';
import { ForumCommunityRule } from './entities/community-rule.entity';
import { ForumCommunityLink } from './entities/community-link.entity';
import { ForumCommunityMember } from './entities/community-member.entity';
import { VoteModule } from '../vote/vote.module';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      ForumPost,
      ForumCommunity,
      ForumComment,
      ForumTag,
      ForumPostTagRelation,
      ForumCommunityRule,
      ForumCommunityLink,
      ForumCommunityMember,
    ]),
    VoteModule,
    AuthModule,
  ],
  providers: [ForumService],
  controllers: [ForumController],
  exports: [ForumService],
})
export class ForumModule {}
