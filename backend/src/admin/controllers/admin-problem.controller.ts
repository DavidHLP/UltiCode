import {
  Controller,
  Get,
  Post,
  Patch,
  Delete,
  Param,
  Body,
  Query,
  UseGuards,
  Res,
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
import {
  PermissionAction,
  PermissionResource,
  Prisma,
  Difficulty as PrismaDifficulty,
  ProblemStatus,
} from '@prisma/client';
import {
  CreateProblemDto,
  UpdateProblemDto,
  ProblemQueryDto,
  BulkProblemActionDto,
  ImportProblemsDto,
  Difficulty,
} from '../dto/problem.dto';
import DOMPurify from 'dompurify';

// Map Prisma difficulty to frontend UPPERCASE format
function mapDifficultyToFrontend(
  prismaDifficulty: PrismaDifficulty,
): Difficulty {
  switch (prismaDifficulty) {
    case PrismaDifficulty.Easy:
      return Difficulty.EASY;
    case PrismaDifficulty.Medium:
      return Difficulty.MEDIUM;
    case PrismaDifficulty.Hard:
      return Difficulty.HARD;
    default:
      return Difficulty.EASY;
  }
}

// Map frontend difficulty to Prisma format
function mapDifficultyToPrisma(difficulty: Difficulty): PrismaDifficulty {
  switch (difficulty) {
    case Difficulty.EASY:
      return PrismaDifficulty.Easy;
    case Difficulty.MEDIUM:
      return PrismaDifficulty.Medium;
    case Difficulty.HARD:
      return PrismaDifficulty.Hard;
    default:
      return PrismaDifficulty.Easy;
  }
}

/**
 * Sanitize markdown content to prevent XSS attacks.
 *
 * For the summary/content field, we want to:
 * 1. Allow storing raw markdown (for proper rendering)
 * 2. Strip any HTML tags that might have been injected
 * 3. Keep the text content intact
 *
 * This is a defense-in-depth measure - the frontend also sanitizes
 * when rendering, but we sanitize here too in case the API is called
 * directly or the frontend protection fails.
 */
function sanitizeMarkdown(content: string): string {
  if (!content) return content;
  // Strip all HTML tags but keep the text content
  // This prevents any HTML/JS injection while preserving markdown
  return DOMPurify.sanitize(content, {
    ALLOWED_TAGS: [], // Disallow all HTML tags
    ALLOWED_ATTR: [], // Disallow all attributes
    KEEP_CONTENT: true, // Keep text content
  });
}

@Controller('admin/problems')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard, CsrfGuard)
export class AdminProblemController {
  constructor(
    private prisma: PrismaService,
    private auditService: AuditService,
  ) {}

  // Helper to fetch complete problem data with all relations
  private async getCompleteProblem(id: bigint) {
    return this.prisma.problem.findUnique({
      where: { id },
      include: {
        detail: true,
        examples: {
          orderBy: { example_order: 'asc' },
        },
        languages: true,
        tagRelations: {
          include: {
            tag: true,
          },
        },
        _count: {
          select: {
            submissions: true,
            solutions: true,
          },
        },
      },
    });
  }

  // Helper to transform problem data for frontend
  private transformProblemForFrontend(
    problem: Awaited<ReturnType<typeof this.getCompleteProblem>>,
  ) {
    if (!problem) return null;

    // Transform examples
    const transformedExamples = problem.examples.map((ex) => ({
      id: ex.id,
      input: ex.input_text,
      output: ex.output_text,
      explanation: ex.explanation,
      order: ex.example_order,
    }));

    // Transform languages
    const transformedLanguages = problem.languages.map((lang) => ({
      id: lang.id,
      language: lang.label,
      value: lang.value,
      style: lang.style,
      starter_code: lang.starter_code,
    }));

    // Transform detail
    const transformedDetail = problem.detail
      ? {
          id: problem.detail.id,
          summary: problem.detail.summary,
          content: problem.detail.summary,
          difficulty_rating: problem.detail.difficulty_rating
            ? Number(problem.detail.difficulty_rating)
            : 0,
          likes: problem.detail.likes,
          dislikes: problem.detail.dislikes,
          constraints_json: problem.detail.constraints_json as
            | string[]
            | undefined,
          hints: problem.detail.hints as string[] | undefined,
        }
      : null;

    return {
      id: problem.id.toString(),
      slug: problem.slug,
      title: problem.title,
      difficulty: mapDifficultyToFrontend(problem.difficulty),
      status: problem.status,
      is_premium: problem.is_premium,
      has_solution: problem.has_solution,
      is_published: problem.is_published,
      published_at: problem.published_at,
      published_by: problem.published_by,
      is_deleted: problem.is_deleted,
      deleted_at: problem.deleted_at,
      created_at: problem.published_at || new Date(),
      updated_at: problem.detail?.updated_at || new Date(),
      detail: transformedDetail,
      tags: problem.tagRelations.map((tr) => ({
        id: tr.tag.id,
        label: tr.tag.label,
      })),
      examples: transformedExamples,
      languages: transformedLanguages,
      submission_count: problem._count.submissions,
      solution_count: problem._count.solutions,
    };
  }

  @Get()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  async findAll(@Query() query: ProblemQueryDto) {
    const {
      search,
      difficulty,
      status,
      is_published,
      is_deleted,
      tag,
      page = 1,
      limit = 20,
      sortBy = 'id',
      sortOrder = 'desc',
    } = query;

    const skip = (page - 1) * limit;

    // Build where clause
    const where: Prisma.ProblemWhereInput = {};

    if (search) {
      where.OR = [
        { title: { contains: search } },
        { slug: { contains: search } },
      ];
    }

    if (difficulty) {
      where.difficulty = mapDifficultyToPrisma(difficulty);
    }

    if (status) {
      where.status = status;
    }

    if (is_published !== undefined) {
      where.is_published = is_published;
    }

    if (is_deleted !== undefined) {
      where.is_deleted = is_deleted;
    }

    if (tag) {
      where.tagRelations = {
        some: {
          tag: {
            label: tag,
          },
        },
      };
    }

    const problems = await this.prisma.problem.findMany({
      where,
      skip,
      take: limit,
      orderBy: { [sortBy]: sortOrder },
      include: {
        detail: {
          select: {
            id: true,
            summary: true,
            difficulty_rating: true,
            likes: true,
            dislikes: true,
            updated_at: true,
          },
        },
        tagRelations: {
          include: {
            tag: {
              select: {
                id: true,
                label: true,
              },
            },
          },
        },
        _count: {
          select: {
            submissions: true,
            solutions: true,
          },
        },
      },
    });
    const total = await this.prisma.problem.count({ where });

    return {
      data: problems.map((p) => ({
        id: p.id.toString(),
        slug: p.slug,
        title: p.title,
        difficulty: mapDifficultyToFrontend(p.difficulty),
        status: p.status,
        is_premium: p.is_premium,
        has_solution: p.has_solution,
        is_published: p.is_published,
        published_at: p.published_at,
        published_by: p.published_by,
        is_deleted: p.is_deleted,
        deleted_at: p.deleted_at,
        created_at: p.published_at || new Date(),
        updated_at: p.detail?.updated_at || new Date(),
        detail: p.detail
          ? {
              id: p.detail.id,
              summary: p.detail.summary,
              difficulty_rating: p.detail.difficulty_rating
                ? Number(p.detail.difficulty_rating)
                : 0,
              likes: p.detail.likes,
              dislikes: p.detail.dislikes,
            }
          : null,
        tags: p.tagRelations.map((tr) => tr.tag),
        submission_count: p._count.submissions,
        solution_count: p._count.solutions,
      })),
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
  }

  @Get('flagged')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  async getFlaggedProblems(
    @Query('page') page = 1,
    @Query('limit') limit = 20,
    @Query('status') status?: 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED',
  ) {
    const skip = (page - 1) * limit;

    const where: Prisma.ProblemWhereInput = {
      is_flagged: true,
    };

    if (status) {
      where.flag_status = status;
    }

    const problems = await this.prisma.problem.findMany({
      where,
      skip,
      take: limit,
      orderBy: { flag_reported_at: 'desc' },
      include: {
        detail: {
          select: {
            id: true,
            summary: true,
            difficulty_rating: true,
            likes: true,
            dislikes: true,
            updated_at: true,
          },
        },
        tagRelations: {
          include: {
            tag: {
              select: {
                id: true,
                label: true,
              },
            },
          },
        },
        _count: {
          select: {
            submissions: true,
            solutions: true,
          },
        },
      },
    });

    const total = await this.prisma.problem.count({ where });

    return {
      data: problems.map((p) => ({
        id: p.id.toString(),
        slug: p.slug,
        title: p.title,
        difficulty: mapDifficultyToFrontend(p.difficulty),
        status: p.status,
        is_premium: p.is_premium,
        has_solution: p.has_solution,
        is_published: p.is_published,
        is_flagged: p.is_flagged,
        flag_reason: p.flag_reason,
        flag_reported_by: p.flag_reported_by,
        flag_reported_at: p.flag_reported_at,
        flag_status: p.flag_status,
        flag_reviewed_by: p.flag_reviewed_by,
        flag_reviewed_at: p.flag_reviewed_at,
        flag_notes: p.flag_notes,
        created_at: p.published_at || new Date(),
        updated_at: p.detail?.updated_at || new Date(),
        detail: p.detail
          ? {
              id: p.detail.id,
              summary: p.detail.summary,
              difficulty_rating: p.detail.difficulty_rating
                ? Number(p.detail.difficulty_rating)
                : 0,
              likes: p.detail.likes,
              dislikes: p.detail.dislikes,
            }
          : null,
        tags: p.tagRelations.map((tr) => tr.tag),
        submission_count: p._count.submissions,
        solution_count: p._count.solutions,
      })),
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
  }

  @Get(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  async findOne(@Param('id') id: string) {
    const problem = await this.getCompleteProblem(BigInt(id));
    return this.transformProblemForFrontend(problem);
  }

  @Post()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.CREATE,
    resource: PermissionResource.PROBLEM,
  })
  async create(
    @Body() createProblemDto: CreateProblemDto,
    @CurrentAdmin() admin: User,
  ) {
    const {
      slug,
      title,
      difficulty,
      status,
      is_premium,
      is_published,
      summary,
      examples,
      constraints,
      hints,
      languages,
      tags,
    } = createProblemDto;

    // Sanitize markdown content to prevent XSS
    const sanitizedSummary = summary ? sanitizeMarkdown(summary) : undefined;

    // Generate a unique ID for the problem
    const id = BigInt(Date.now());

    const prismaDifficulty = mapDifficultyToPrisma(difficulty);

    await this.prisma.problem.create({
      data: {
        id,
        slug,
        title,
        difficulty: prismaDifficulty,
        status: status || 'todo',
        is_premium: is_premium || false,
        is_published: is_published || false,
        published_at: is_published ? new Date() : null,
        published_by: is_published ? admin.id : null,
        detail: sanitizedSummary
          ? {
              create: {
                id: crypto.randomUUID(),
                slug,
                summary: sanitizedSummary,
                constraints_json: constraints || [],
                hints,
                updated_at: new Date(),
              },
            }
          : undefined,
        examples: examples
          ? {
              create: examples.map((ex, idx) => ({
                id: crypto.randomUUID(),
                problem_id: id,
                input_text: ex.input,
                output_text: ex.output,
                explanation: ex.explanation
                  ? sanitizeMarkdown(ex.explanation)
                  : undefined,
                example_order: idx,
              })),
            }
          : undefined,
        languages: languages
          ? {
              create: languages.map((lang) => ({
                id: crypto.randomUUID(),
                problem_id: id,
                label: lang,
                value: lang.toLowerCase(),
                starter_code: '// Write your code here',
              })),
            }
          : undefined,
      },
    });

    // Add tags if provided
    if (tags && tags.length > 0) {
      for (const tagLabel of tags) {
        let tag = await this.prisma.problemTag.findFirst({
          where: { label: tagLabel },
        });

        if (!tag) {
          tag = await this.prisma.problemTag.create({
            data: {
              id: crypto.randomUUID(),
              label: tagLabel,
            },
          });
        }

        await this.prisma.problemTagRelation.create({
          data: {
            problem_id: id,
            tag_id: tag.id,
          },
        });
      }
    }

    // Fetch complete problem data for audit snapshot
    const completeProblem = await this.getCompleteProblem(id);
    const transformedProblem =
      this.transformProblemForFrontend(completeProblem);

    if (!transformedProblem) {
      throw new Error('Failed to create problem');
    }

    await this.auditService.log({
      performerId: admin.id,
      action: 'CREATE_PROBLEM',
      entityType: 'PROBLEM',
      entityId: id.toString(),
      newValues: {
        ...transformedProblem,
        // Include all related data for version history
        detail: transformedProblem.detail,
        examples: transformedProblem.examples,
        languages: transformedProblem.languages,
        tags: transformedProblem.tags,
      },
    });

    // Return complete problem data
    return transformedProblem;
  }

  @Patch(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.PROBLEM,
  })
  async update(
    @Param('id') id: string,
    @Body() updateProblemDto: UpdateProblemDto,
    @CurrentAdmin() admin: User,
  ) {
    const problemId = BigInt(id);
    const oldProblem = await this.prisma.problem.findUnique({
      where: { id: problemId },
    });

    if (!oldProblem) {
      return null;
    }

    const {
      slug,
      title,
      difficulty,
      status,
      is_premium,
      has_solution,
      summary,
      constraints,
      hints,
    } = updateProblemDto;

    const updateData: Prisma.ProblemUpdateInput = {};
    const detailUpdate: Prisma.ProblemDetailUpdateWithoutProblemInput = {};

    if (slug !== undefined) updateData.slug = slug;
    if (title !== undefined) updateData.title = title;
    if (difficulty !== undefined) {
      updateData.difficulty = mapDifficultyToPrisma(difficulty);
    }
    if (status !== undefined) updateData.status = status;
    if (is_premium !== undefined) updateData.is_premium = is_premium;
    if (has_solution !== undefined) updateData.has_solution = has_solution;

    // Sanitize markdown content to prevent XSS
    if (summary !== undefined) detailUpdate.summary = sanitizeMarkdown(summary);
    if (constraints !== undefined) detailUpdate.constraints_json = constraints;
    if (hints !== undefined) detailUpdate.hints = hints;

    // If we have detail updates, ensure we update updated_at
    if (Object.keys(detailUpdate).length > 0) {
      detailUpdate.updated_at = new Date();
    }

    await this.prisma.problem.update({
      where: { id: problemId },
      data: {
        ...updateData,
        detail:
          Object.keys(detailUpdate).length > 0
            ? {
                update: detailUpdate,
              }
            : undefined,
      },
    });

    // Fetch complete problem data for audit snapshots
    const oldCompleteProblem = await this.getCompleteProblem(problemId);
    const oldTransformedProblem =
      this.transformProblemForFrontend(oldCompleteProblem);
    const newCompleteProblem = await this.getCompleteProblem(problemId);
    const newTransformedProblem =
      this.transformProblemForFrontend(newCompleteProblem);

    if (!oldTransformedProblem || !newTransformedProblem) {
      throw new Error('Failed to update problem');
    }

    await this.auditService.log({
      performerId: admin.id,
      action: 'UPDATE_PROBLEM',
      entityType: 'PROBLEM',
      entityId: id,
      oldValues: {
        ...oldTransformedProblem,
        // Include all related data for version history
        detail: oldTransformedProblem.detail,
        examples: oldTransformedProblem.examples,
        languages: oldTransformedProblem.languages,
        tags: oldTransformedProblem.tags,
      },
      newValues: {
        ...newTransformedProblem,
        // Include all related data for version history
        detail: newTransformedProblem.detail,
        examples: newTransformedProblem.examples,
        languages: newTransformedProblem.languages,
        tags: newTransformedProblem.tags,
      },
    });

    // Return complete problem data
    return newTransformedProblem;
  }

  @Delete(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.DELETE,
    resource: PermissionResource.PROBLEM,
  })
  async remove(@Param('id') id: string, @CurrentAdmin() admin: User) {
    const oldProblem = await this.prisma.problem.findUnique({
      where: { id: BigInt(id) },
    });

    // Soft delete
    await this.prisma.problem.update({
      where: { id: BigInt(id) },
      data: {
        is_deleted: true,
        deleted_at: new Date(),
        deleted_by: admin.id,
      },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'DELETE_PROBLEM',
      entityType: 'PROBLEM',
      entityId: id,
      oldValues: {
        slug: oldProblem?.slug,
        title: oldProblem?.title,
      },
    });

    return { message: 'Problem deleted successfully' };
  }

  @Post(':id/publish')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.PUBLISH,
    resource: PermissionResource.PROBLEM,
  })
  async publish(@Param('id') id: string, @CurrentAdmin() admin: User) {
    const problemId = BigInt(id);

    await this.prisma.problem.update({
      where: { id: problemId },
      data: {
        is_published: true,
        published_at: new Date(),
        published_by: admin.id,
      },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'PUBLISH_PROBLEM',
      entityType: 'PROBLEM',
      entityId: id,
      newValues: { is_published: true },
    });

    // Return complete problem data
    const completeProblem = await this.getCompleteProblem(problemId);
    return this.transformProblemForFrontend(completeProblem);
  }

  @Post(':id/unpublish')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.PUBLISH,
    resource: PermissionResource.PROBLEM,
  })
  async unpublish(@Param('id') id: string, @CurrentAdmin() admin: User) {
    const problemId = BigInt(id);

    await this.prisma.problem.update({
      where: { id: problemId },
      data: {
        is_published: false,
      },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'UNPUBLISH_PROBLEM',
      entityType: 'PROBLEM',
      entityId: id,
      oldValues: { is_published: true },
      newValues: { is_published: false },
    });

    // Return complete problem data
    const completeProblem = await this.getCompleteProblem(problemId);
    return this.transformProblemForFrontend(completeProblem);
  }

  @Get(':id/submissions')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  async getSubmissions(
    @Param('id') id: string,
    @Query('page') page = 1,
    @Query('limit') limit = 20,
  ) {
    const skip = (page - 1) * limit;

    const submissions = await this.prisma.submission.findMany({
      where: { problem_id: BigInt(id) },
      skip,
      take: limit,
      orderBy: { created_at: 'desc' },
      include: {
        user: {
          select: {
            id: true,
            username: true,
            name: true,
          },
        },
      },
    });
    const total = await this.prisma.submission.count({
      where: { problem_id: BigInt(id) },
    });

    return {
      data: submissions.map((s) => ({
        ...s,
        id: s.id.toString(),
        problem_id: s.problem_id.toString(),
        user_id: s.user_id.toString(),
      })),
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
  }

  @Post('bulk')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.PROBLEM,
  })
  async bulkAction(
    @Body() bulkDto: BulkProblemActionDto,
    @CurrentAdmin() admin: User,
  ) {
    const { ids, action } = bulkDto;
    const results: { id: string; success: boolean; error?: string }[] = [];

    for (const id of ids) {
      try {
        switch (action) {
          case 'publish':
            await this.prisma.problem.update({
              where: { id: BigInt(id) },
              data: {
                is_published: true,
                published_at: new Date(),
                published_by: admin.id,
              },
            });
            break;
          case 'unpublish':
            await this.prisma.problem.update({
              where: { id: BigInt(id) },
              data: { is_published: false },
            });
            break;
          case 'delete':
            await this.prisma.problem.update({
              where: { id: BigInt(id) },
              data: {
                is_deleted: true,
                deleted_at: new Date(),
                deleted_by: admin.id,
              },
            });
            break;
          case 'restore':
            await this.prisma.problem.update({
              where: { id: BigInt(id) },
              data: {
                is_deleted: false,
                deleted_at: null,
                deleted_by: null,
              },
            });
            break;
        }
        results.push({ id, success: true });
      } catch (_error) {
        results.push({ id, success: false, error: 'Failed to perform action' });
      }
    }

    await this.auditService.log({
      performerId: admin.id,
      action: `BULK_${action.toUpperCase()}_PROBLEMS`,
      entityType: 'PROBLEM',
      entityId: ids.join(','),
      newValues: { action, count: ids.length },
    });

    return { results };
  }

  @Get(':id/versions')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  async getVersions(@Param('id') id: string) {
    const auditLogs = await this.prisma.auditLog.findMany({
      where: {
        entity_type: 'PROBLEM',
        entity_id: id,
        action: {
          in: ['CREATE_PROBLEM', 'UPDATE_PROBLEM'],
        },
      },
      orderBy: { created_at: 'desc' },
      include: {
        performer: {
          select: {
            id: true,
            username: true,
            name: true,
            role: true,
          },
        },
      },
    });

    return {
      data: auditLogs.map((log) => ({
        id: log.id,
        action: log.action,
        performer: {
          id: log.performer.id,
          username: log.performer.username,
          name: log.performer.name,
          role: log.performer.role,
        },
        entityType: log.entity_type,
        entityId: log.entity_id,
        oldValues: log.old_values as Record<string, unknown> | undefined,
        newValues: log.new_values as Record<string, unknown> | undefined,
        createdAt: log.created_at.toISOString(),
      })),
      total: auditLogs.length,
    };
  }

  @Post(':id/versions/:versionId/restore')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.PROBLEM,
  })
  async restoreVersion(
    @Param('id') id: string,
    @Param('versionId') versionId: string,
    @CurrentAdmin() admin: User,
  ) {
    // Fetch the audit log for the version to restore
    const auditLog = await this.prisma.auditLog.findUnique({
      where: { id: versionId },
    });

    if (
      !auditLog ||
      auditLog.entity_id !== id ||
      auditLog.entity_type !== 'PROBLEM'
    ) {
      throw new Error('Version not found or does not belong to this problem');
    }

    // Extract the snapshot from the audit log
    const snapshot = auditLog.new_values as Record<string, unknown>;
    if (!snapshot) {
      throw new Error('No snapshot data found in version');
    }

    const problemId = BigInt(id);

    // Fetch current problem data for audit log
    const oldCompleteProblem = await this.getCompleteProblem(problemId);
    const oldTransformedProblem =
      this.transformProblemForFrontend(oldCompleteProblem);

    // Restore the problem from the snapshot
    const updateData: Prisma.ProblemUpdateInput = {};
    const detailUpdate: Prisma.ProblemDetailUpdateWithoutProblemInput = {};

    // Restore basic fields
    if (snapshot.slug) updateData.slug = snapshot.slug as string;
    if (snapshot.title) updateData.title = snapshot.title as string;
    if (snapshot.difficulty) {
      updateData.difficulty = mapDifficultyToPrisma(
        snapshot.difficulty as Difficulty,
      );
    }
    if (snapshot.status) {
      updateData.status = snapshot.status as ProblemStatus;
    }
    if (snapshot.is_premium !== undefined) {
      updateData.is_premium = snapshot.is_premium as boolean;
    }
    if (snapshot.has_solution !== undefined) {
      updateData.has_solution = snapshot.has_solution as boolean;
    }

    // Restore detail fields
    if (snapshot.detail) {
      const detail = snapshot.detail as Record<string, unknown>;
      if (detail.summary) detailUpdate.summary = detail.summary as string;
      if (detail.constraints_json) {
        detailUpdate.constraints_json = detail.constraints_json as string[];
      }
      if (detail.hints) detailUpdate.hints = detail.hints as string[];
      detailUpdate.updated_at = new Date();
    }

    // Update the problem
    await this.prisma.problem.update({
      where: { id: problemId },
      data: {
        ...updateData,
        detail:
          Object.keys(detailUpdate).length > 0
            ? {
                update: detailUpdate,
              }
            : undefined,
      },
    });

    // Log the restore operation
    await this.auditService.log({
      performerId: admin.id,
      action: 'RESTORE_PROBLEM_VERSION',
      entityType: 'PROBLEM',
      entityId: id,
      oldValues: oldTransformedProblem,
      newValues: {
        ...snapshot,
        restoredFromVersion: versionId,
        restoredAt: new Date().toISOString(),
      },
    });

    // Return the restored problem
    const newCompleteProblem = await this.getCompleteProblem(problemId);
    return this.transformProblemForFrontend(newCompleteProblem);
  }

  @Get('export')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  async exportProblems(
    @Query() query: ProblemQueryDto,
    @Query('format') format: 'json' | 'csv' = 'json',
    @Res()
    res: {
      set: (headers: Record<string, string>) => void;
      send: (data: string) => void;
      json: (data: unknown) => void;
    },
  ) {
    const { search, difficulty, status, is_published, is_deleted, tag } = query;

    // Build where clause
    const where: Prisma.ProblemWhereInput = {};

    if (search) {
      where.OR = [
        { title: { contains: search } },
        { slug: { contains: search } },
      ];
    }

    if (difficulty) {
      where.difficulty = mapDifficultyToPrisma(difficulty);
    }

    if (status) {
      where.status = status;
    }

    if (is_published !== undefined) {
      where.is_published = is_published;
    }

    if (is_deleted !== undefined) {
      where.is_deleted = is_deleted;
    }

    if (tag) {
      where.tagRelations = {
        some: {
          tag: {
            label: tag,
          },
        },
      };
    }

    const problems = await this.prisma.problem.findMany({
      where,
      orderBy: { id: 'asc' },
      include: {
        detail: true,
        examples: {
          orderBy: { example_order: 'asc' },
        },
        languages: true,
        tagRelations: {
          include: {
            tag: true,
          },
        },
      },
    });

    const exportData = problems.map((problem) => ({
      id: problem.id.toString(),
      slug: problem.slug,
      title: problem.title,
      difficulty: mapDifficultyToFrontend(problem.difficulty),
      status: problem.status,
      is_premium: problem.is_premium,
      has_solution: problem.has_solution,
      is_published: problem.is_published,
      published_at: problem.published_at,
      published_by: problem.published_by,
      is_deleted: problem.is_deleted,
      deleted_at: problem.deleted_at,
      deleted_by: problem.deleted_by,
      detail: problem.detail
        ? {
            summary: problem.detail.summary,
            constraints_json: problem.detail.constraints_json,
            hints: problem.detail.hints,
          }
        : null,
      examples: problem.examples.map((ex) => ({
        input: ex.input_text,
        output: ex.output_text,
        explanation: ex.explanation,
      })),
      languages: problem.languages.map((lang) => ({
        label: lang.label,
        value: lang.value,
        starter_code: lang.starter_code,
      })),
      tags: problem.tagRelations.map((tr) => tr.tag.label),
    }));

    if (format === 'csv') {
      // CSV format
      const headers = [
        'id',
        'slug',
        'title',
        'difficulty',
        'status',
        'is_premium',
        'has_solution',
        'is_published',
        'published_at',
        'is_deleted',
        'deleted_at',
        'summary',
        'constraints',
        'hints',
        'tags',
      ];

      const rows = exportData.map((p) => [
        p.id,
        p.slug,
        `"${p.title.replace(/"/g, '""')}"`,
        p.difficulty,
        p.status,
        p.is_premium,
        p.has_solution,
        p.is_published,
        p.published_at || '',
        p.is_deleted,
        p.deleted_at || '',
        `"${(p.detail?.summary || '').replace(/"/g, '""')}"`,
        `"${JSON.stringify(p.detail?.constraints_json || []).replace(
          /"/g,
          '""',
        )}"`,
        `"${JSON.stringify(p.detail?.hints || []).replace(/"/g, '""')}"`,
        `"${p.tags.join(', ').replace(/"/g, '""')}"`,
      ]);

      const csvContent = [
        headers.join(','),
        ...rows.map((row) => row.join(',')),
      ].join('\n');

      res.set({
        'Content-Type': 'text/csv',
        'Content-Disposition': `attachment; filename=problems-export-${new Date().toISOString().split('T')[0]}.csv`,
      });
      res.send(csvContent);
    } else {
      // JSON format
      res.set({
        'Content-Type': 'application/json',
        'Content-Disposition': `attachment; filename=problems-export-${new Date().toISOString().split('T')[0]}.json`,
      });
      res.json({
        exportedAt: new Date().toISOString(),
        count: exportData.length,
        data: exportData,
      });
    }
  }

  @Post('import')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.CREATE,
    resource: PermissionResource.PROBLEM,
  })
  async importProblems(
    @Body() importDto: ImportProblemsDto,
    @CurrentAdmin() admin: User,
  ) {
    const { problems, onConflict = 'skip' } = importDto;
    const results: {
      slug: string;
      success: boolean;
      error?: string;
      action?: 'created' | 'updated' | 'skipped';
    }[] = [];

    for (const problemData of problems) {
      try {
        // Check if problem already exists
        const existingProblem = await this.prisma.problem.findFirst({
          where: { slug: problemData.slug },
        });

        if (existingProblem) {
          // Handle conflict based on strategy
          if (onConflict === 'skip') {
            results.push({
              slug: problemData.slug,
              success: true,
              action: 'skipped',
            });
            continue;
          }

          if (onConflict === 'create_new') {
            // Generate a new slug
            const baseSlug = problemData.slug;
            let newSlug = baseSlug;
            let counter = 1;
            while (
              await this.prisma.problem.findFirst({ where: { slug: newSlug } })
            ) {
              newSlug = `${baseSlug}-${counter}`;
              counter++;
            }
            problemData.slug = newSlug;
          }
        }

        // Sanitize markdown content
        const sanitizedSummary = problemData.summary
          ? sanitizeMarkdown(problemData.summary)
          : undefined;

        const prismaDifficulty = mapDifficultyToPrisma(problemData.difficulty);

        // Use transaction to ensure data consistency
        await this.prisma.$transaction(async (tx) => {
          let problemId: bigint;

          if (existingProblem && onConflict === 'update') {
            // Update existing problem
            problemId = existingProblem.id;

            const updateData: Prisma.ProblemUpdateInput = {};
            const detailUpdate: Prisma.ProblemDetailUpdateWithoutProblemInput =
              {};

            if (problemData.title) updateData.title = problemData.title;
            if (problemData.difficulty) {
              updateData.difficulty = prismaDifficulty;
            }
            if (problemData.status) updateData.status = problemData.status;
            if (problemData.is_premium !== undefined) {
              updateData.is_premium = problemData.is_premium;
            }
            if (problemData.has_solution !== undefined) {
              updateData.has_solution = problemData.has_solution;
            }
            if (problemData.is_published !== undefined) {
              updateData.is_published = problemData.is_published;
            }

            if (sanitizedSummary) detailUpdate.summary = sanitizedSummary;
            if (problemData.constraints) {
              detailUpdate.constraints_json = problemData.constraints;
            }
            if (problemData.hints) detailUpdate.hints = problemData.hints;
            if (Object.keys(detailUpdate).length > 0) {
              detailUpdate.updated_at = new Date();
            }

            await tx.problem.update({
              where: { id: problemId },
              data: {
                ...updateData,
                detail:
                  Object.keys(detailUpdate).length > 0
                    ? {
                        update: detailUpdate,
                      }
                    : undefined,
              },
            });

            // Delete existing examples and recreate
            await tx.problemExample.deleteMany({
              where: { problem_id: problemId },
            });
            // Delete existing languages and recreate
            await tx.problemLanguage.deleteMany({
              where: { problem_id: problemId },
            });
            // Delete existing tag relations and recreate
            await tx.problemTagRelation.deleteMany({
              where: { problem_id: problemId },
            });

            results.push({
              slug: problemData.slug,
              success: true,
              action: 'updated',
            });
          } else {
            // Create new problem
            problemId = BigInt(Date.now() + Math.random() * 1000);

            await tx.problem.create({
              data: {
                id: problemId,
                slug: problemData.slug,
                title: problemData.title,
                difficulty: prismaDifficulty,
                status: problemData.status || 'todo',
                is_premium: problemData.is_premium || false,
                has_solution: problemData.has_solution || false,
                is_published: problemData.is_published || false,
                published_at: problemData.is_published ? new Date() : null,
                published_by: problemData.is_published ? admin.id : null,
                detail: sanitizedSummary
                  ? {
                      create: {
                        id: crypto.randomUUID(),
                        slug: problemData.slug,
                        summary: sanitizedSummary,
                        constraints_json: problemData.constraints || [],
                        hints: problemData.hints,
                        updated_at: new Date(),
                      },
                    }
                  : undefined,
                examples: problemData.examples
                  ? {
                      create: problemData.examples.map((ex, idx) => ({
                        id: crypto.randomUUID(),
                        problem_id: problemId,
                        input_text: ex.input,
                        output_text: ex.output,
                        explanation: ex.explanation
                          ? sanitizeMarkdown(ex.explanation)
                          : undefined,
                        example_order: idx,
                      })),
                    }
                  : undefined,
                languages: problemData.languages
                  ? {
                      create: problemData.languages.map((lang) => ({
                        id: crypto.randomUUID(),
                        problem_id: problemId,
                        label: lang.label,
                        value: lang.value,
                        starter_code: lang.starter_code,
                      })),
                    }
                  : undefined,
              },
            });

            results.push({
              slug: problemData.slug,
              success: true,
              action: 'created',
            });
          }

          // Add or update tags
          if (problemData.tags && problemData.tags.length > 0) {
            for (const tagLabel of problemData.tags) {
              let tag = await tx.problemTag.findFirst({
                where: { label: tagLabel },
              });

              if (!tag) {
                tag = await tx.problemTag.create({
                  data: {
                    id: crypto.randomUUID(),
                    label: tagLabel,
                  },
                });
              }

              await tx.problemTagRelation.upsert({
                where: {
                  problem_id_tag_id: {
                    problem_id: problemId,
                    tag_id: tag.id,
                  },
                },
                create: {
                  problem_id: problemId,
                  tag_id: tag.id,
                },
                update: {},
              });
            }
          }
        });
      } catch (error) {
        results.push({
          slug: problemData.slug,
          success: false,
          error: error instanceof Error ? error.message : 'Unknown error',
        });
      }
    }

    // Log the import operation
    await this.auditService.log({
      performerId: admin.id,
      action: 'IMPORT_PROBLEMS',
      entityType: 'PROBLEM',
      entityId: problems.map((p) => p.slug).join(','),
      newValues: {
        total: problems.length,
        created: results.filter((r) => r.action === 'created').length,
        updated: results.filter((r) => r.action === 'updated').length,
        skipped: results.filter((r) => r.action === 'skipped').length,
        failed: results.filter((r) => !r.success).length,
        onConflict,
      },
    });

    return {
      total: problems.length,
      created: results.filter((r) => r.action === 'created').length,
      updated: results.filter((r) => r.action === 'updated').length,
      skipped: results.filter((r) => r.action === 'skipped').length,
      failed: results.filter((r) => !r.success).length,
      results,
    };
  }

  @Post(':id/flag')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.PROBLEM,
  })
  async flagProblem(
    @Param('id') id: string,
    @Body() flagDto: { reason: string },
    @CurrentAdmin() admin: User,
  ) {
    const problemId = BigInt(id);

    await this.prisma.problem.update({
      where: { id: problemId },
      data: {
        is_flagged: true,
        flag_reason: flagDto.reason,
        flag_reported_by: admin.id,
        flag_reported_at: new Date(),
        flag_status: 'PENDING',
      },
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'FLAG_PROBLEM',
      entityType: 'PROBLEM',
      entityId: id,
      newValues: {
        is_flagged: true,
        flag_reason: flagDto.reason,
        flag_status: 'PENDING',
      },
    });

    // Return complete problem data
    const completeProblem = await this.getCompleteProblem(problemId);
    return this.transformProblemForFrontend(completeProblem);
  }

  @Post(':id/moderate')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.PROBLEM,
  })
  async moderateProblem(
    @Param('id') id: string,
    @Body()
    moderationDto: {
      status: 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED';
      notes?: string;
    },
    @CurrentAdmin() admin: User,
  ) {
    const problemId = BigInt(id);

    const updateData: Prisma.ProblemUpdateInput = {
      flag_status: moderationDto.status,
      flag_reviewed_by: admin.id,
      flag_reviewed_at: new Date(),
    };

    if (moderationDto.notes !== undefined) {
      updateData.flag_notes = moderationDto.notes;
    }

    // If the status is RESOLVED or DISMISSED, unflag the problem
    if (
      moderationDto.status === 'RESOLVED' ||
      moderationDto.status === 'DISMISSED'
    ) {
      updateData.is_flagged = false;
    }

    await this.prisma.problem.update({
      where: { id: problemId },
      data: updateData,
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'MODERATE_PROBLEM',
      entityType: 'PROBLEM',
      entityId: id,
      newValues: {
        flag_status: moderationDto.status,
        flag_notes: moderationDto.notes,
        is_flagged:
          moderationDto.status === 'RESOLVED' ||
          moderationDto.status === 'DISMISSED'
            ? false
            : true,
      },
    });

    // Return complete problem data
    const completeProblem = await this.getCompleteProblem(problemId);
    return this.transformProblemForFrontend(completeProblem);
  }
}
