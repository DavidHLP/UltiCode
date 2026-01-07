import {
  Injectable,
  NotFoundException,
  BadRequestException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { AuditService } from './audit.service';
import {
  TagQueryDto,
  CreateTagDto,
  UpdateTagDto,
  TagType,
} from '../dto/tag.dto';
import { Prisma } from '@prisma/client';

@Injectable()
export class AdminTagService {
  constructor(
    private prisma: PrismaService,
    private auditService: AuditService,
  ) {}

  private slugify(text: string): string {
    return text
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)+/g, '');
  }

  async findAll(query: TagQueryDto) {
    const {
      search,
      type,
      page = 1,
      limit = 20,
      sortBy = 'usage_count',
      sortOrder = 'desc',
    } = query;
    const skip = (page - 1) * limit;

    // Handle Problem Tags
    if (type === TagType.PROBLEM) {
      const where: Prisma.ProblemTagWhereInput = {};
      if (search) {
        where.OR = [
          { label: { contains: search } },
          { slug: { contains: search } },
        ];
      }

      const [data, total] = await Promise.all([
        this.prisma.problemTag.findMany({
          where,
          skip,
          take: limit,
          orderBy: { [sortBy === 'name' ? 'label' : sortBy]: sortOrder },
        }),
        this.prisma.problemTag.count({ where }),
      ]);

      return {
        data: data.map((tag) => ({
          id: tag.id,
          name: tag.label,
          slug: tag.slug,
          description: tag.description,
          color: tag.color,
          usage_count: tag.usage_count,
          type: TagType.PROBLEM,
          created_at: tag.created_at,
          updated_at: tag.updated_at,
        })),
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
      };
    }

    // Handle Forum Tags
    else if (type === TagType.FORUM) {
      const where: Prisma.ForumTagWhereInput = {};
      if (search) {
        where.OR = [
          { name: { contains: search } },
          { slug: { contains: search } },
        ];
      }

      const [data, total] = await Promise.all([
        this.prisma.forumTag.findMany({
          where,
          skip,
          take: limit,
          orderBy: { [sortBy]: sortOrder },
        }),
        this.prisma.forumTag.count({ where }),
      ]);

      return {
        data: data.map((tag) => ({
          ...tag,
          type: TagType.FORUM,
        })),
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
      };
    }

    // Handle All Tags (Mixed) - Complex pagination, simplified for now:
    // If no type specified, we default to PROBLEM tags or we could fetch both and merge manually.
    // For Admin UI consistency, it's better to force a type filter or default to one.
    // Let's default to PROBLEM if not specified for now, or throw error.
    // But better: Return separate lists? No, UI expects one list.
    // Decision: If type is missing, return Problem Tags (primary use case).
    return this.findAll({ ...query, type: TagType.PROBLEM });
  }

  async findOne(id: string, type: TagType) {
    if (type === TagType.PROBLEM) {
      const tag = await this.prisma.problemTag.findUnique({ where: { id } });
      if (!tag) throw new NotFoundException('Problem tag not found');
      return { ...tag, name: tag.label, type: TagType.PROBLEM };
    } else {
      const tag = await this.prisma.forumTag.findUnique({ where: { id } });
      if (!tag) throw new NotFoundException('Forum tag not found');
      return { ...tag, type: TagType.FORUM };
    }
  }

  async create(createTagDto: CreateTagDto, adminId: string) {
    const { name, slug, description, color, type } = createTagDto;
    const generatedSlug = slug || this.slugify(name);

    if (type === TagType.PROBLEM) {
      const tag = await this.prisma.problemTag.create({
        data: {
          id: crypto.randomUUID(),
          label: name,
          slug: generatedSlug,
          description,
          color,
        },
      });
      await this.auditService.log({
        performerId: adminId,
        action: 'CREATE_TAG',
        entityType: 'PROBLEM_TAG',
        entityId: tag.id,
        newValues: createTagDto,
      });
      return tag;
    } else {
      const tag = await this.prisma.forumTag.create({
        data: {
          id: crypto.randomUUID(),
          name,
          slug: generatedSlug,
          description,
          color,
        },
      });
      await this.auditService.log({
        performerId: adminId,
        action: 'CREATE_TAG',
        entityType: 'FORUM_TAG',
        entityId: tag.id,
        newValues: createTagDto,
      });
      return tag;
    }
  }

  async update(
    id: string,
    updateTagDto: UpdateTagDto,
    type: TagType,
    adminId: string,
  ) {
    if (type === TagType.PROBLEM) {
      const oldTag = await this.prisma.problemTag.findUnique({ where: { id } });
      if (!oldTag) throw new NotFoundException('Tag not found');

      const data: Prisma.ProblemTagUpdateInput = {};
      if (updateTagDto.name) data.label = updateTagDto.name;
      if (updateTagDto.slug) data.slug = updateTagDto.slug;
      if (updateTagDto.description !== undefined)
        data.description = updateTagDto.description;
      if (updateTagDto.color !== undefined) data.color = updateTagDto.color;

      const tag = await this.prisma.problemTag.update({
        where: { id },
        data,
      });

      await this.auditService.log({
        performerId: adminId,
        action: 'UPDATE_TAG',
        entityType: 'PROBLEM_TAG',
        entityId: id,
        oldValues: oldTag,
        newValues: updateTagDto,
      });

      return tag;
    } else {
      const oldTag = await this.prisma.forumTag.findUnique({ where: { id } });
      if (!oldTag) throw new NotFoundException('Tag not found');

      const data: Prisma.ForumTagUpdateInput = {};
      if (updateTagDto.name) data.name = updateTagDto.name;
      if (updateTagDto.slug) data.slug = updateTagDto.slug;
      if (updateTagDto.description !== undefined)
        data.description = updateTagDto.description;
      if (updateTagDto.color !== undefined) data.color = updateTagDto.color;

      const tag = await this.prisma.forumTag.update({
        where: { id },
        data,
      });

      await this.auditService.log({
        performerId: adminId,
        action: 'UPDATE_TAG',
        entityType: 'FORUM_TAG',
        entityId: id,
        oldValues: oldTag,
        newValues: updateTagDto,
      });

      return tag;
    }
  }

  async delete(id: string, type: TagType, adminId: string) {
    if (type === TagType.PROBLEM) {
      await this.prisma.problemTag.delete({ where: { id } });
      await this.auditService.log({
        performerId: adminId,
        action: 'DELETE_TAG',
        entityType: 'PROBLEM_TAG',
        entityId: id,
      });
    } else {
      await this.prisma.forumTag.delete({ where: { id } });
      await this.auditService.log({
        performerId: adminId,
        action: 'DELETE_TAG',
        entityType: 'FORUM_TAG',
        entityId: id,
      });
    }
    return { success: true };
  }

  async merge(
    sourceId: string,
    targetId: string,
    type: TagType,
    adminId: string,
  ) {
    if (sourceId === targetId)
      throw new BadRequestException('Cannot merge tag into itself');

    if (type === TagType.PROBLEM) {
      const [source, target] = await Promise.all([
        this.prisma.problemTag.findUnique({ where: { id: sourceId } }),
        this.prisma.problemTag.findUnique({ where: { id: targetId } }),
      ]);

      if (!source || !target)
        throw new NotFoundException('Source or target tag not found');

      // Move all relations from source to target
      // We need to be careful about duplicates (problem already has target tag)
      const sourceRelations = await this.prisma.problemTagRelation.findMany({
        where: { tag_id: sourceId },
      });

      let movedCount = 0;
      for (const rel of sourceRelations) {
        // Check if target already exists for this problem
        const exists = await this.prisma.problemTagRelation.findFirst({
          where: { problem_id: rel.problem_id, tag_id: targetId },
        });

        if (!exists) {
          // Update relation to point to target
          await this.prisma.problemTagRelation.update({
            where: {
              problem_id_tag_id: {
                problem_id: rel.problem_id,
                tag_id: sourceId,
              },
            },
            data: { tag_id: targetId },
          });
          movedCount++;
        } else {
          // Just delete the old relation
          await this.prisma.problemTagRelation.delete({
            where: {
              problem_id_tag_id: {
                problem_id: rel.problem_id,
                tag_id: sourceId,
              },
            },
          });
        }
      }

      // Delete source tag
      await this.prisma.problemTag.delete({ where: { id: sourceId } });

      // Update usage count
      const newCount = await this.prisma.problemTagRelation.count({
        where: { tag_id: targetId },
      });
      await this.prisma.problemTag.update({
        where: { id: targetId },
        data: { usage_count: newCount },
      });

      await this.auditService.log({
        performerId: adminId,
        action: 'MERGE_TAG',
        entityType: 'PROBLEM_TAG',
        entityId: targetId,
        oldValues: { sourceId, sourceLabel: source.label },
        newValues: { movedRelations: movedCount },
      });

      return { success: true, movedCount };
    } else {
      // Forum Tag Merge
      const [source, target] = await Promise.all([
        this.prisma.forumTag.findUnique({ where: { id: sourceId } }),
        this.prisma.forumTag.findUnique({ where: { id: targetId } }),
      ]);

      if (!source || !target)
        throw new NotFoundException('Source or target tag not found');

      // 1. Move Post Relations
      const postRelations = await this.prisma.forumPostTagRelation.findMany({
        where: { tag_id: sourceId },
      });

      let movedPosts = 0;
      for (const rel of postRelations) {
        const exists = await this.prisma.forumPostTagRelation.findUnique({
          where: {
            post_id_tag_id: {
              post_id: rel.post_id,
              tag_id: targetId,
            },
          },
        });

        if (!exists) {
          await this.prisma.forumPostTagRelation.update({
            where: {
              post_id_tag_id: {
                post_id: rel.post_id,
                tag_id: sourceId,
              },
            },
            data: { tag_id: targetId },
          });
          movedPosts++;
        } else {
          await this.prisma.forumPostTagRelation.delete({
            where: {
              post_id_tag_id: {
                post_id: rel.post_id,
                tag_id: sourceId,
              },
            },
          });
        }
      }

      // 2. Move Community Relations
      const communityRelations = await this.prisma.forumCommunityTag.findMany({
        where: { tag_id: sourceId },
      });

      let movedCommunities = 0;
      for (const rel of communityRelations) {
        const exists = await this.prisma.forumCommunityTag.findUnique({
          where: {
            community_id_tag_id: {
              community_id: rel.community_id,
              tag_id: targetId,
            },
          },
        });

        if (!exists) {
          await this.prisma.forumCommunityTag.update({
            where: {
              community_id_tag_id: {
                community_id: rel.community_id,
                tag_id: sourceId,
              },
            },
            data: { tag_id: targetId },
          });
          movedCommunities++;
        } else {
          await this.prisma.forumCommunityTag.delete({
            where: {
              community_id_tag_id: {
                community_id: rel.community_id,
                tag_id: sourceId,
              },
            },
          });
        }
      }

      // Delete source tag
      await this.prisma.forumTag.delete({ where: { id: sourceId } });

      // Update usage count logic if needed (ForumTag has usage_count field)
      // Usually would be recalculated or incremented
      await this.prisma.forumTag.update({
        where: { id: targetId },
        data: { usage_count: { increment: source.usage_count } }, // Approximate
      });

      await this.auditService.log({
        performerId: adminId,
        action: 'MERGE_TAG',
        entityType: 'FORUM_TAG',
        entityId: targetId,
        oldValues: { sourceId, sourceName: source.name },
        newValues: { movedPosts, movedCommunities },
      });

      return { success: true, movedPosts, movedCommunities };
    }
  }
}
