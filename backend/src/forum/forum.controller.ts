import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Patch,
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
import { CreatePostDto } from './dto/create-post.dto';
import { UpdatePostDto } from './dto/update-post.dto';

interface AuthenticatedRequest extends Request {
  user: {
    id: string;
    username: string;
    avatar?: string | null;
  };
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

  @UseGuards(AuthGuard)
  @Get('me/posts')
  findMyPosts(@Req() req: AuthenticatedRequest): Promise<ForumPost[]> {
    return this.forumService.findPostsByUser(req.user.id);
  }

  @UseGuards(AuthGuard)
  @Post('posts')
  createPost(@Body() body: CreatePostDto, @Req() req: AuthenticatedRequest) {
    const user = req.user;
    return this.forumService.createPost(
      {
        title: body.title,
        excerpt: body.excerpt ?? body.body ?? null,
        communityId: body.communityId,
        tags: body.tags,
        flairType: body.flairType,
        flairLabel: body.flairLabel,
        media: body.media,
      },
      { id: user.id, username: user.username, avatar: user.avatar },
    );
  }

  @UseGuards(AuthGuard)
  @Patch('posts/:id')
  updatePost(
    @Param('id') postId: string,
    @Body() body: UpdatePostDto,
    @Req() req: AuthenticatedRequest,
  ) {
    return this.forumService.updatePost(postId, req.user.id, {
      title: body.title,
      excerpt: body.excerpt ?? body.body,
      tags: body.tags,
      flairType: body.flairType,
      flairLabel: body.flairLabel,
      media: body.media,
      isPinned: body.isPinned,
      isLocked: body.isLocked,
    });
  }

  @UseGuards(AuthGuard)
  @Delete('posts/:id')
  deletePost(@Param('id') postId: string, @Req() req: AuthenticatedRequest) {
    return this.forumService.deletePost(postId, req.user.id);
  }

  @Get('posts/:id/thread')
  findThread(
    @Param('id') id: string,
    @Query('userId') userId?: string,
  ): Promise<(ForumPost & { comments: ForumComment[] }) | null> {
    return this.forumService.getThread(id, userId);
  }

  // Community endpoints
  @Get('communities')
  findAllCommunities(
    @Query('featured') featured?: string,
  ): Promise<ForumCommunity[]> {
    return this.forumService.findAllCommunities({
      featuredOnly: featured === 'true',
    });
  }

  @Get('communities/:slugOrId')
  async findOneCommunity(@Param('slugOrId') slugOrId: string) {
    return this.forumService.findOneCommunity(slugOrId);
  }

  @Get('communities/:slug/posts')
  findPostsByCommunity(
    @Param('slug') slug: string,
    @Query('sortBy') sortBy?: 'hot' | 'new' | 'top',
  ) {
    return this.forumService.findPostsByCommunity(slug, { sortBy });
  }

  // Tag endpoints
  @Get('tags')
  findAllTags() {
    return this.forumService.findAllTags();
  }

  // Membership endpoints
  @UseGuards(AuthGuard)
  @Post('communities/:id/join')
  joinCommunity(@Param('id') id: string, @Req() req: AuthenticatedRequest) {
    return this.forumService.joinCommunity(req.user.id, id);
  }

  @UseGuards(AuthGuard)
  @Post('communities/:id/leave')
  leaveCommunity(@Param('id') id: string, @Req() req: AuthenticatedRequest) {
    return this.forumService.leaveCommunity(req.user.id, id);
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
    return this.forumService.createComment(postId, body.body, body.parentId, {
      id: user.id,
      username: user.username,
      avatar: user.avatar,
    });
  }

  @UseGuards(AuthGuard)
  @Patch('comments/:id')
  updateComment(
    @Param('id') commentId: string,
    @Body() body: { body: string },
    @Req() req: AuthenticatedRequest,
  ) {
    return this.forumService.updateComment(commentId, body.body, req.user.id);
  }

  @UseGuards(AuthGuard)
  @Delete('comments/:id')
  deleteComment(
    @Param('id') commentId: string,
    @Req() req: AuthenticatedRequest,
  ) {
    return this.forumService.deleteComment(commentId, req.user.id);
  }
}
