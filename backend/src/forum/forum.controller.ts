import { Controller, Get, Param } from '@nestjs/common';
import { ForumService } from './forum.service';
import { ForumPost } from './forum-post.entity';
import { ForumCommunity } from './forum-community.entity';
import { ForumComment } from './forum-comment.entity';
import forumData from '../../prisma/seed/data/forum.data';

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
  getTrending() {
    // Return trending topics from seed data
    return forumData.forum_trending_topics.map((topic) => ({
      id: topic.id,
      name: topic.title,
      title: topic.title,
      posts_count: topic.posts,
      trend: topic.trend,
    }));
  }

  @Get('moderators')
  getModerators() {
    // Return moderators from seed data
    return forumData.forum_moderators.map((mod) => ({
      id: mod.id,
      username: mod.username,
      role: 'moderator',
      avatar: null,
    }));
  }

  @Get('quick-filters')
  getQuickFilters() {
    return forumData.forum_quick_filters;
  }
}
