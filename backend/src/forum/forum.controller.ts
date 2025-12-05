import { Controller, Get, Param } from '@nestjs/common';
import { ForumService } from './forum.service';
import { ForumPost } from './forum-post.entity';
import { ForumCommunity } from './forum-community.entity';
import { ForumComment } from './forum-comment.entity';
import type {
  ForumModerator,
  ForumQuickFilter,
  ForumTrendingTopic,
} from './forum.service';

@Controller('forum')
export class ForumController {
  constructor(private readonly forumService: ForumService) {}

  @Get('posts')
  findAllPosts(): Promise<ForumPost[]> {
    return this.forumService.findAllPosts();
  }

  @Get('posts/:id')
  findOnePost(@Param('id') id: string): Promise<ForumPost | null> {
    return this.forumService.findOnePost(id);
  }

  @Get('posts/:id/thread')
  findThread(
    @Param('id') id: string,
  ): Promise<(ForumPost & { comments: ForumComment[] }) | null> {
    return this.forumService.getThread(id);
  }

  @Get('communities')
  findAllCommunities(): Promise<ForumCommunity[]> {
    return this.forumService.findAllCommunities();
  }

  @Get('trending')
  getTrendingTopics(): ForumTrendingTopic[] {
    return this.forumService.getTrendingTopics();
  }

  @Get('moderators')
  getModerators(): ForumModerator[] {
    return this.forumService.getModerators();
  }

  @Get('quick-filters')
  getQuickFilters(): ForumQuickFilter[] {
    return this.forumService.getQuickFilters();
  }
}
