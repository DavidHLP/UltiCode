import {
  Controller,
  Get,
  Post,
  Delete,
  Param,
  Body,
  Query,
  UseGuards,
} from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { RequireRoles } from '../decorators/roles.decorator';
import { CurrentAdmin } from '../decorators/current-admin.decorator';
import { AuditService } from '../services/audit.service';
import { PrismaService } from '../../prisma.service';
import { UserRole, User } from '../../user/user.entity';
import { PermissionAction, PermissionResource, Prisma } from '@prisma/client';
import {
  ForumPostQueryDto,
  ForumCommentQueryDto,
  BulkForumActionDto,
  BulkForumAction,
} from '../dto/forum.dto';

@Controller('admin/forum')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard, CsrfGuard)
export class AdminForumController {
  constructor(
    private prisma: PrismaService,
    private auditService: AuditService,
  ) {}

  @Get('posts')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  async getPosts(@Query() query: ForumPostQueryDto) {
    const {
      search,
      communityId,
      authorId,
      is_flagged,
      is_pinned,
      is_locked,
      page = 1,
      limit = 20,
      sortBy = 'created_at',
      sortOrder = 'desc',
    } = query;

    const skip = (page - 1) * limit;

    const where: Prisma.ForumPostWhereInput = {
      is_deleted: false,
    };

    if (search) {
      where.OR = [
        { title: { contains: search } },
        { excerpt: { contains: search } },
      ];
    }

    if (communityId) {
      where.community_id = communityId;
    }

    if (authorId) {
      where.user_id = authorId;
    }

    if (is_flagged !== undefined) {
      where.is_flagged = is_flagged;
    }

    if (is_pinned !== undefined) {
      where.is_pinned = is_pinned;
    }

    if (is_locked !== undefined) {
      where.is_locked = is_locked;
    }

    const posts = await this.prisma.forumPost.findMany({
      where,
      skip,
      take: limit,
      orderBy: { [sortBy]: sortOrder },
      include: {
        author: {
          select: {
            id: true,
            username: true,
          },
        },
        community: {
          select: {
            id: true,
            name: true,
            slug: true,
          },
        },
        _count: {
          select: {
            comments: true,
          },
        },
      },
    });
    const total = await this.prisma.forumPost.count({ where });

    type ForumPostWithCount = Prisma.ForumPostGetPayload<{
      include: {
        author: { select: { id: true; username: true } };
        community: { select: { id: true; name: true; slug: true } };
        _count: { select: { comments: true } };
      };
    }>;

    return {
      data: (posts as ForumPostWithCount[]).map((p) => ({
        ...p,
        id: p.id.toString(),
        user_id: p.user_id.toString(),
        community_id: p.community_id?.toString(),
        comment_count: p._count.comments,
        _count: undefined,
      })),
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
  }

  @Get('comments')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  getComments(@Query() _query: ForumCommentQueryDto) {
    // Deprecated: Use AdminCommentController instead
    return {
      data: [],
      total: 0,
      page: 1,
      limit: 20,
      totalPages: 0,
      message: 'This endpoint is deprecated. Use /admin/comments instead.',
    };
  }

  @Get('flagged')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  async getFlaggedContent(@Query('page') page = 1, @Query('limit') limit = 20) {
    const skip = (page - 1) * limit;

    const posts = await this.prisma.forumPost.findMany({
      where: { is_flagged: true, is_deleted: false },
      skip,
      take: limit,
      orderBy: { flagged_at: 'desc' },
      include: {
        author: {
          select: {
            id: true,
            username: true,
            avatar: true,
          },
        },
        community: {
          select: {
            id: true,
            name: true,
          },
        },
      },
    });
    const postTotal = await this.prisma.forumPost.count({
      where: { is_flagged: true, is_deleted: false },
    });

    // Comments don't have is_flagged in schema, so returning empty for comments
    const comments: any[] = [];
    const commentTotal = 0;

    return {
      posts: {
        data: posts.map((p) => ({
          ...p,
          id: p.id.toString(),
          user_id: p.user_id.toString(),
          type: 'post',
        })),
        total: postTotal,
      },
      comments: {
        data: comments,
        total: commentTotal,
      },
      total: postTotal + commentTotal,
    };
  }

  @Get('communities')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.FORUM_POST,
  })
  async getCommunities(
    @Query('page') page: string | number = 1,
    @Query('limit') limit: string | number = 20,
  ) {
    const pageNumber = typeof page === 'string' ? parseInt(page, 10) : page;
    const limitNumber = typeof limit === 'string' ? parseInt(limit, 10) : limit;
    const safePage =
      Number.isFinite(pageNumber) && pageNumber > 0 ? pageNumber : 1;
    const safeLimit =
      Number.isFinite(limitNumber) && limitNumber > 0 ? limitNumber : 20;
    const skip = (safePage - 1) * safeLimit;

    const communities = await this.prisma.forumCommunity.findMany({
      skip,
      take: safeLimit,
      orderBy: { created_at: 'desc' },
      include: {
        _count: {
          select: {
            posts: true,
            members_rel: true, // Renamed from members to members_rel in schema? Check schema.
            // Schema: members_rel ForumCommunityMember[]
            // But members Int (count) exists on ForumCommunity directly!
          },
        },
      },
    });
    const total = await this.prisma.forumCommunity.count();

    return {
      data: communities.map((c) => ({
        ...c,
        post_count: c.posts_count, // Use pre-calculated field or relation count
        member_count: c.members, // Use pre-calculated field
        _count: undefined,
      })),
      total,
      page: safePage,
      limit: safeLimit,
      totalPages: Math.ceil(total / safeLimit),
    };
  }

  @Get('posts/:id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  async getPostDetail(@Param('id') id: string) {
    const post = await this.prisma.forumPost.findUnique({
      where: { id: id },
      include: {
        author: {
          select: {
            id: true,
            username: true,
            avatar: true,
          },
        },
        community: {
          select: {
            id: true,
            name: true,
            slug: true,
          },
        },
        _count: {
          select: {
            comments: true,
          },
        },
      },
    });

    if (!post) {
      throw new Error('Post not found');
    }

    type ForumPostDetail = Prisma.ForumPostGetPayload<{
      include: {
        author: { select: { id: true; username: true; avatar: true } };
        community: { select: { id: true; name: true; slug: true } };
        _count: { select: { comments: true } };
      };
    }>;

    const result = post as ForumPostDetail;
    return {
      ...result,
      id: result.id.toString(),
      user_id: result.user_id.toString(),
      community_id: result.community_id?.toString(),
      comment_count: result._count.comments,
      _count: undefined,
    };
  }

  @Get('posts/:id/audit')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  async getPostAuditHistory(@Param('id') id: string) {
    const auditLogs = await this.prisma.auditLog.findMany({
      where: {
        entity_type: 'FORUM_POST',
        entity_id: id,
      },
      orderBy: {
        created_at: 'desc',
      },
      take: 100,
    });

    // Get performer user IDs
    const performerIds = auditLogs
      .map((log) => log.performer_id)
      .filter(Boolean);
    const users = await this.prisma.user.findMany({
      where: {
        id: { in: performerIds },
      },
      select: {
        id: true,
        username: true,
      },
    });

    const userMap = new Map(users.map((u) => [u.id, u]));

    return {
      data: auditLogs.map((log) => ({
        id: log.id,
        action: log.action,
        performer: {
          id: log.performer_id || '',
          username: userMap.get(log.performer_id)?.username || 'Unknown',
        },
        entityType: log.entity_type,
        entityId: log.entity_id,
        oldValues: log.old_values as Record<string, unknown> | undefined,
        newValues: log.new_values as Record<string, unknown> | undefined,
        ipAddress: log.ip_address,
        userAgent: log.user_agent,
        created_at: log.created_at.toISOString(),
      })),
    };
  }

  @Post('posts/:id/flag')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  async flagPost(
    @Param('id') id: string,
    @Body() body: { reason: string },
    @CurrentAdmin() admin: User,
  ) {
    await this.prisma.forumPost.update({
      where: { id: id },
      data: {
        is_flagged: true,
        flagged_reason: body.reason,
        flagged_at: new Date(),
      },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'FLAG_FORUM_POST',
      entityType: 'FORUM_POST',
      entityId: id,
      newValues: {
        is_flagged: true,
        flagged_reason: body.reason,
      },
    });

    return { message: 'Post flagged successfully' };
  }

  @Post('posts/:id/unflag')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  async unflagPost(@Param('id') id: string, @CurrentAdmin() admin: User) {
    await this.prisma.forumPost.update({
      where: { id: id },
      data: {
        is_flagged: false,
        flagged_reason: null,
        flagged_at: null,
      },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'UNFLAG_FORUM_POST',
      entityType: 'FORUM_POST',
      entityId: id,
      newValues: {
        is_flagged: false,
      },
    });

    return { message: 'Post unflagged successfully' };
  }

  @Delete('posts/:id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  async deletePost(@Param('id') id: string, @CurrentAdmin() admin: User) {
    await this.prisma.forumPost.update({
      where: { id: id },
      data: {
        is_deleted: true,
        deleted_at: new Date(),
        deleted_by: admin.id,
      },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'DELETE_FORUM_POST',
      entityType: 'FORUM_POST',
      entityId: id,
    });

    return { message: 'Post deleted successfully' };
  }

  @Post('posts/:id/hide')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  hidePost(@Param('id') _id: string, @CurrentAdmin() _admin: User) {
    return { message: 'Hide post not supported' };
  }

  @Post('posts/:id/show')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  showPost(@Param('id') _id: string, @CurrentAdmin() _admin: User) {
    return { message: 'Show post not supported' };
  }

  @Post('posts/:id/pin')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  async pinPost(@Param('id') id: string, @CurrentAdmin() admin: User) {
    await this.prisma.forumPost.update({
      where: { id: id },
      data: { is_pinned: true },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'PIN_FORUM_POST',
      entityType: 'FORUM_POST',
      entityId: id,
    });

    return { message: 'Post pinned successfully' };
  }

  @Post('posts/:id/unpin')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  async unpinPost(@Param('id') id: string, @CurrentAdmin() admin: User) {
    await this.prisma.forumPost.update({
      where: { id: id },
      data: { is_pinned: false },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'UNPIN_FORUM_POST',
      entityType: 'FORUM_POST',
      entityId: id,
    });

    return { message: 'Post unpinned successfully' };
  }

  @Post('posts/:id/lock')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  async lockPost(@Param('id') id: string, @CurrentAdmin() admin: User) {
    await this.prisma.forumPost.update({
      where: { id: id },
      data: { is_locked: true },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'LOCK_FORUM_POST',
      entityType: 'FORUM_POST',
      entityId: id,
    });

    return { message: 'Post locked successfully' };
  }

  @Post('posts/:id/unlock')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  async unlockPost(@Param('id') id: string, @CurrentAdmin() admin: User) {
    await this.prisma.forumPost.update({
      where: { id: id },
      data: { is_locked: false },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'UNLOCK_FORUM_POST',
      entityType: 'FORUM_POST',
      entityId: id,
    });

    return { message: 'Post unlocked successfully' };
  }

  @Delete('comments/:id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  deleteComment(@Param('id') _id: string, @CurrentAdmin() _admin: User) {
    // Deprecated: Use AdminCommentController instead
    return {
      message: 'This endpoint is deprecated. Use /admin/comments/:id instead.',
    };
  }

  @Post('bulk')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  async bulkAction(
    @Body() bulkDto: BulkForumActionDto,
    @CurrentAdmin() admin: User,
  ) {
    const { ids, action } = bulkDto;
    const results: { id: string; success: boolean; error?: string }[] = [];

    for (const id of ids) {
      try {
        // Check if it's a post or comment based on prefix or try both
        let success = false;

        // Try as post first
        try {
          const updateData: Prisma.ForumPostUpdateInput = {};
          let shouldUpdate = true;
          switch (action) {
            case BulkForumAction.DELETE:
              updateData.is_deleted = true;
              updateData.deleted_at = new Date();
              updateData.deleted_by = admin.id;
              break;
            case BulkForumAction.HIDE:
              // Not supported
              shouldUpdate = false;
              break;
            case BulkForumAction.SHOW:
              // Not supported
              shouldUpdate = false;
              break;
            case BulkForumAction.PIN:
              updateData.is_pinned = true;
              break;
            case BulkForumAction.UNPIN:
              updateData.is_pinned = false;
              break;
            case BulkForumAction.LOCK:
              updateData.is_locked = true;
              break;
            case BulkForumAction.UNLOCK:
              updateData.is_locked = false;
              break;
            case BulkForumAction.UNFLAG:
              updateData.is_flagged = false;
              updateData.flagged_at = null;
              break;
          }
          if (shouldUpdate) {
            await this.prisma.forumPost.update({
              where: { id: id },
              data: updateData,
            });
            success = true;
          }
        } catch {
          // Try as comment
          try {
            if (action === (BulkForumAction.DELETE as any)) {
              await this.prisma.forumComment.delete({
                where: { id: id },
              });
              success = true;
            } else {
              // Other actions might not be supported for comments or require different logic
              // Comment has is_pinned, is_locked. No is_flagged.
              const updateData: Prisma.ForumCommentUpdateInput = {};
              let shouldUpdate = true;
              switch (action as any) {
                case BulkForumAction.PIN:
                  updateData.is_pinned = true;
                  break;
                case BulkForumAction.UNPIN:
                  updateData.is_pinned = false;
                  break;
                case BulkForumAction.LOCK:
                  updateData.is_locked = true;
                  break;
                case BulkForumAction.UNLOCK:
                  updateData.is_locked = false;
                  break;
                default:
                  shouldUpdate = false;
                  break;
              }
              if (shouldUpdate) {
                await this.prisma.forumComment.update({
                  where: { id: id },
                  data: updateData,
                });
                success = true;
              }
            }
          } catch {
            // Neither post nor comment found
          }
        }

        results.push({ id, success: success || false });
      } catch (_error) {
        results.push({ id, success: false, error: 'Failed to perform action' });
      }
    }

    await this.auditService.log({
      performerId: admin.id,
      action: `BULK_${action.toUpperCase()}_FORUM`,
      entityType: 'FORUM',
      entityId: ids.join(','),
      newValues: { action, count: ids.length },
    });

    return { results };
  }
}
