import {
  Body,
  Controller,
  Get,
  Param,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { ForumService } from './forum.service';
import { ForumPost } from './entities/post.entity';
import { ForumCommunity } from './entities/community.entity';
import { ForumComment } from './entities/comment.entity';
import forumData from '../../prisma/seed/data/forum.data';
import { AuthGuard } from '../auth/auth.guard';
import type { Request } from 'express';

interface AuthenticatedRequest extends Request {
  user: { id: string };
}

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
    @Query('userId') userId?: string,
  ): Promise<(ForumPost & { comments: ForumComment[] }) | null> {
    return this.forumService.getThread(id, userId);
  }

  @Get('communities')
  findAllCommunities(): Promise<ForumCommunity[]> {
    return this.forumService.findAllCommunities();
  }

  @Get('quick-filters')
  getQuickFilters() {
    return forumData.forum_quick_filters;
  }

  @UseGuards(AuthGuard)
  @Post('posts/:id/comments')
  createComment(
    @Param('id') postId: string,
    @Body() body: { body: string; parentId: string | null },
    @Req() req: AuthenticatedRequest,
  ) {
    const user = req.user;
    return this.forumService.createComment(
      postId,
      body.body,
      body.parentId,
      user.id,
    );
  }
}
