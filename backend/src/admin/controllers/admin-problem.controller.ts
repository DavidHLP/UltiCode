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
} from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
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
} from '@prisma/client';
import {
  CreateProblemDto,
  UpdateProblemDto,
  ProblemQueryDto,
  BulkProblemActionDto,
  Difficulty,
} from '../dto/problem.dto';

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

@Controller('admin/problems')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard)
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
        detail: summary
          ? {
              create: {
                id: crypto.randomUUID(),
                slug,
                summary,
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
                explanation: ex.explanation,
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

    await this.auditService.log({
      performerId: admin.id,
      action: 'CREATE_PROBLEM',
      entityType: 'PROBLEM',
      entityId: id.toString(),
      newValues: { slug, title, difficulty },
    });

    // Return complete problem data
    const completeProblem = await this.getCompleteProblem(id);
    return this.transformProblemForFrontend(completeProblem);
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

    if (summary !== undefined) detailUpdate.summary = summary;
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

    await this.auditService.log({
      performerId: admin.id,
      action: 'UPDATE_PROBLEM',
      entityType: 'PROBLEM',
      entityId: id,
      oldValues: {
        slug: oldProblem.slug,
        title: oldProblem.title,
        difficulty: oldProblem.difficulty,
      },
      newValues: updateProblemDto,
    });

    // Return complete problem data
    const completeProblem = await this.getCompleteProblem(problemId);
    return this.transformProblemForFrontend(completeProblem);
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
}
