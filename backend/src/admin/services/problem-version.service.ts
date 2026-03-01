import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { Difficulty } from '@prisma/client';

export interface CreateVersionData {
  problemId: bigint;
  title: string;
  slug: string;
  difficulty: Difficulty;
  isPremium: boolean;
  isPublished: boolean;
  summary?: string;
  content?: string;
  constraints?: string[];
  hints?: string[];
  examples?: Array<{
    input: string;
    output: string;
    explanation?: string;
    order?: number;
  }>;
  languages?: Array<{
    label: string;
    value: string;
    starter_code: string;
  }>;
  tags?: string[];
  changeSummary?: string;
  changeType?: 'create' | 'update' | 'rollback';
  createdBy?: string;
}

export interface VersionDiff {
  field: string;
  oldValue: unknown;
  newValue: unknown;
}

export interface VersionWithDiff {
  id: string;
  versionNumber: number;
  changeSummary: string | null;
  changeType: string;
  createdAt: Date;
  createdBy: string | null;
  diffs: VersionDiff[];
}

@Injectable()
export class ProblemVersionService {
  private readonly logger = new Logger(ProblemVersionService.name);

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Create a new version snapshot for a problem
   */
  async createVersion(data: CreateVersionData): Promise<void> {
    const { problemId, changeSummary, changeType, createdBy, ...problemData } =
      data;

    // Get current version number
    const problem = await this.prisma.problem.findUnique({
      where: { id: problemId },
      select: { version: true },
    });

    if (!problem) {
      throw new NotFoundException(`Problem ${problemId} not found`);
    }

    // Create version snapshot
    await this.prisma.problemVersion.create({
      data: {
        problem_id: problemId,
        version_number: problem.version,
        title: problemData.title,
        slug: problemData.slug,
        difficulty: problemData.difficulty,
        is_premium: problemData.isPremium,
        is_published: problemData.isPublished,
        summary: problemData.summary,
        content: problemData.content,
        constraints: problemData.constraints ?? undefined,
        hints: problemData.hints ?? undefined,
        examples: problemData.examples ?? undefined,
        languages: problemData.languages ?? undefined,
        tags: problemData.tags ?? undefined,
        change_summary: changeSummary,
        change_type: changeType ?? 'update',
        created_by: createdBy,
      },
    });

    // Increment problem version
    await this.prisma.problem.update({
      where: { id: problemId },
      data: { version: { increment: 1 } },
    });

    this.logger.debug(
      `Created version ${problem.version} for problem ${problemId}`,
    );
  }

  /**
   * Get version history for a problem
   */
  async getVersionHistory(
    problemId: bigint,
    options: { limit?: number; offset?: number } = {},
  ): Promise<{
    versions: Array<{
      id: string;
      versionNumber: number;
      changeSummary: string | null;
      changeType: string;
      createdAt: Date;
      createdBy: string | null;
    }>;
    total: number;
  }> {
    const { limit = 20, offset = 0 } = options;

    const [versions, total] = await Promise.all([
      this.prisma.problemVersion.findMany({
        where: { problem_id: problemId },
        orderBy: { version_number: 'desc' },
        skip: offset,
        take: limit,
        select: {
          id: true,
          version_number: true,
          change_summary: true,
          change_type: true,
          created_at: true,
          created_by: true,
        },
      }),
      this.prisma.problemVersion.count({
        where: { problem_id: problemId },
      }),
    ]);

    return {
      versions: versions.map((v) => ({
        id: v.id,
        versionNumber: v.version_number,
        changeSummary: v.change_summary,
        changeType: v.change_type,
        createdAt: v.created_at,
        createdBy: v.created_by,
      })),
      total,
    };
  }

  /**
   * Get a specific version by ID
   */
  async getVersion(
    problemId: bigint,
    versionId: string,
  ): Promise<{
    id: string;
    versionNumber: number;
    title: string;
    slug: string;
    difficulty: Difficulty;
    isPremium: boolean;
    isPublished: boolean;
    summary: string | null;
    content: string | null;
    constraints: string[] | null;
    hints: string[] | null;
    examples: Array<{
      input: string;
      output: string;
      explanation?: string;
      order?: number;
    }> | null;
    languages: Array<{
      label: string;
      value: string;
      starter_code: string;
    }> | null;
    tags: string[] | null;
    changeSummary: string | null;
    changeType: string;
    createdAt: Date;
    createdBy: string | null;
  }> {
    const version = await this.prisma.problemVersion.findFirst({
      where: { id: versionId, problem_id: problemId },
    });

    if (!version) {
      throw new NotFoundException(
        `Version ${versionId} not found for problem ${problemId}`,
      );
    }

    return {
      id: version.id,
      versionNumber: version.version_number,
      title: version.title,
      slug: version.slug,
      difficulty: version.difficulty,
      isPremium: version.is_premium,
      isPublished: version.is_published,
      summary: version.summary,
      content: version.content,
      constraints: version.constraints as string[] | null,
      hints: version.hints as string[] | null,
      examples: version.examples as Array<{
        input: string;
        output: string;
        explanation?: string;
        order?: number;
      }> | null,
      languages: version.languages as Array<{
        label: string;
        value: string;
        starter_code: string;
      }> | null,
      tags: version.tags as string[] | null,
      changeSummary: version.change_summary,
      changeType: version.change_type,
      createdAt: version.created_at,
      createdBy: version.created_by,
    };
  }

  /**
   * Get diff between two versions
   */
  async getVersionDiff(
    problemId: bigint,
    fromVersionId: string,
    toVersionId: string,
  ): Promise<VersionWithDiff> {
    const [fromVersion, toVersion] = await Promise.all([
      this.getVersion(problemId, fromVersionId),
      this.getVersion(problemId, toVersionId),
    ]);

    const diffs: VersionDiff[] = [];

    // Compare fields
    const fieldsToCompare: Array<keyof typeof fromVersion> = [
      'title',
      'slug',
      'difficulty',
      'isPremium',
      'isPublished',
      'summary',
      'content',
    ];

    for (const field of fieldsToCompare) {
      if (fromVersion[field] !== toVersion[field]) {
        diffs.push({
          field,
          oldValue: fromVersion[field],
          newValue: toVersion[field],
        });
      }
    }

    // Compare JSON fields
    const jsonFields: Array<keyof typeof fromVersion> = [
      'constraints',
      'hints',
      'examples',
      'languages',
      'tags',
    ];

    for (const field of jsonFields) {
      const oldVal = JSON.stringify(fromVersion[field] ?? []);
      const newVal = JSON.stringify(toVersion[field] ?? []);
      if (oldVal !== newVal) {
        diffs.push({
          field,
          oldValue: fromVersion[field],
          newValue: toVersion[field],
        });
      }
    }

    return {
      id: toVersion.id,
      versionNumber: toVersion.versionNumber,
      changeSummary: toVersion.changeSummary,
      changeType: toVersion.changeType,
      createdAt: toVersion.createdAt,
      createdBy: toVersion.createdBy,
      diffs,
    };
  }

  /**
   * Rollback to a specific version
   */
  async rollbackToVersion(
    problemId: bigint,
    targetVersionId: string,
    rolledBackBy?: string,
  ): Promise<void> {
    const targetVersion = await this.getVersion(problemId, targetVersionId);

    // Get current problem state
    const currentProblem = await this.prisma.problem.findUnique({
      where: { id: problemId },
      include: {
        detail: true,
        tagRelations: { include: { tag: true } },
        examples: { orderBy: { example_order: 'asc' } },
        languages: true,
      },
    });

    if (!currentProblem) {
      throw new NotFoundException(`Problem ${problemId} not found`);
    }

    // Create a snapshot of current state before rollback
    await this.createVersion({
      problemId,
      title: currentProblem.title,
      slug: currentProblem.slug,
      difficulty: currentProblem.difficulty,
      isPremium: currentProblem.is_premium,
      isPublished: currentProblem.is_published,
      summary: currentProblem.detail?.summary ?? undefined,
      content: currentProblem.detail?.summary ?? undefined,
      constraints:
        (currentProblem.detail?.constraints_json as string[]) ?? undefined,
      hints: (currentProblem.detail?.hints as string[]) ?? undefined,
      examples: currentProblem.examples.map((e) => ({
        input: e.input_text,
        output: e.output_text,
        explanation: e.explanation ?? undefined,
        order: e.example_order,
      })),
      languages: currentProblem.languages.map((l) => ({
        label: l.label,
        value: l.value,
        starter_code: l.starter_code,
      })),
      tags: currentProblem.tagRelations.map((t) => t.tag.label),
      changeSummary: `Auto-snapshot before rollback to version ${targetVersion.versionNumber}`,
      changeType: 'update',
      createdBy: rolledBackBy,
    });

    // Update problem with target version data
    await this.prisma.$transaction(async (tx) => {
      // Update main problem fields
      await tx.problem.update({
        where: { id: problemId },
        data: {
          title: targetVersion.title,
          slug: targetVersion.slug,
          difficulty: targetVersion.difficulty,
          is_premium: targetVersion.isPremium,
          is_published: targetVersion.isPublished,
        },
      });

      // Update detail if exists
      if (
        targetVersion.summary ||
        targetVersion.constraints ||
        targetVersion.hints
      ) {
        await tx.problemDetail.upsert({
          where: { problem_id: problemId },
          create: {
            id: `pd_${Date.now()}`,
            problem_id: problemId,
            slug: targetVersion.slug,
            summary: targetVersion.summary ?? '',
            constraints_json: targetVersion.constraints ?? [],
            hints: targetVersion.hints ?? [],
            updated_at: new Date(),
          },
          update: {
            summary: targetVersion.summary ?? '',
            constraints_json: targetVersion.constraints ?? [],
            hints: targetVersion.hints ?? [],
            updated_at: new Date(),
          },
        });
      }

      // Update examples
      if (targetVersion.examples) {
        await tx.problemExample.deleteMany({
          where: { problem_id: problemId },
        });

        for (const example of targetVersion.examples) {
          await tx.problemExample.create({
            data: {
              id: `pe_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
              problem_id: problemId,
              input_text: example.input,
              output_text: example.output,
              explanation: example.explanation ?? null,
              example_order: example.order ?? 0,
            },
          });
        }
      }

      // Update languages
      if (targetVersion.languages) {
        await tx.problemLanguage.deleteMany({
          where: { problem_id: problemId },
        });

        for (const lang of targetVersion.languages) {
          await tx.problemLanguage.create({
            data: {
              id: `pl_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
              problem_id: problemId,
              label: lang.label,
              value: lang.value,
              starter_code: lang.starter_code,
            },
          });
        }
      }

      // Update tags
      if (targetVersion.tags) {
        // Remove existing tag relations
        await tx.problemTagRelation.deleteMany({
          where: { problem_id: problemId },
        });

        // Add new tag relations
        for (const tagLabel of targetVersion.tags) {
          const tag = await tx.problemTag.findFirst({
            where: { label: tagLabel },
          });

          if (tag) {
            await tx.problemTagRelation.create({
              data: {
                problem_id: problemId,
                tag_id: tag.id,
              },
            });
          }
        }
      }

      // Create version record for rollback
      await tx.problemVersion.create({
        data: {
          problem_id: problemId,
          version_number: (await tx.problem.findUnique({
            where: { id: problemId },
            select: { version: true },
          }))!.version,
          title: targetVersion.title,
          slug: targetVersion.slug,
          difficulty: targetVersion.difficulty,
          is_premium: targetVersion.isPremium,
          is_published: targetVersion.isPublished,
          summary: targetVersion.summary,
          content: targetVersion.content,
          constraints: targetVersion.constraints ?? undefined,
          hints: targetVersion.hints ?? undefined,
          examples: targetVersion.examples ?? undefined,
          languages: targetVersion.languages ?? undefined,
          tags: targetVersion.tags ?? undefined,
          change_summary: `Rolled back to version ${targetVersion.versionNumber}`,
          change_type: 'rollback',
          created_by: rolledBackBy,
        },
      });

      // Increment version
      await tx.problem.update({
        where: { id: problemId },
        data: { version: { increment: 1 } },
      });
    });

    this.logger.log(
      `Rolled back problem ${problemId} to version ${targetVersion.versionNumber}`,
    );
  }

  /**
   * Delete old versions (cleanup)
   */
  async cleanupOldVersions(
    problemId: bigint,
    keepLast: number = 50,
  ): Promise<number> {
    const versions = await this.prisma.problemVersion.findMany({
      where: { problem_id: problemId },
      orderBy: { version_number: 'desc' },
      select: { id: true },
      skip: keepLast,
    });

    if (versions.length === 0) {
      return 0;
    }

    const idsToDelete = versions.map((v) => v.id);

    await this.prisma.problemVersion.deleteMany({
      where: { id: { in: idsToDelete } },
    });

    this.logger.debug(
      `Deleted ${idsToDelete.length} old versions for problem ${problemId}`,
    );

    return idsToDelete.length;
  }
}
