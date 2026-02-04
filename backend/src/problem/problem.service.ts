import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import type { Problem, ProblemDetail, ProblemTag } from '@prisma/client';
import { CATEGORY_TAG_MAP } from './constants';
import { I18nService } from '../i18n/i18n.service';
import {
  SupportedLocale,
  DEFAULT_LOCALE,
  TRANSLATABLE_ENTITIES,
} from '../i18n/i18n.constants';
import { SubscriptionService } from '../subscription/subscription.service';
import { PaginatedResult } from '../contest/dto/ranking.dto';

// Re-export Problem type from Prisma for backward compatibility
export type { Problem } from '@prisma/client';

// Extended types for relations
export type ProblemWithRelations = Problem & {
  detail?: ProblemDetail | null;
  tagRelations?: Array<{
    tag: ProblemTag;
  }>;
  languages?: Array<{
    id: string;
    label: string;
    value: string;
    style: string | null;
    starter_code: string;
  }>;
  examples?: Array<{
    id: string;
    example_order: number;
    input_text: string;
    output_text: string;
    explanation: string | null;
    inputs: { name: string; value: string }[] | null;
  }>;
};

@Injectable()
export class ProblemService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly i18nService: I18nService,
    private readonly subscriptionService: SubscriptionService,
  ) {}

  async findAll(
    filters: {
      category?: string;
      difficulty?: string;
      search?: string;
      page?: number;
      limit?: number;
    } = {},
    locale: SupportedLocale = DEFAULT_LOCALE,
  ): Promise<PaginatedResult<Problem>> {
    const page = filters.page ?? 1;
    const limit = filters.limit ?? 20;
    const skip = (page - 1) * limit;

    const where: Record<string, unknown> = {};

    if (filters.difficulty) {
      where.difficulty = filters.difficulty;
    }

    if (filters.search) {
      where.OR = [
        { title: { contains: filters.search, mode: 'insensitive' as const } },
        // For ID search, we need to handle it separately since id is BigInt
      ];
    }

    const [problems, total] = await Promise.all([
      this.prisma.problem.findMany({
        where,
        skip,
        take: limit,
        orderBy: { id: 'asc' },
        include: {
          tagRelations: {
            include: {
              tag: true,
            },
          },
        },
      }),
      this.prisma.problem.count({ where }),
    ]);

    // Filter by category after query (since it requires tag filtering)
    let filteredProblems = problems;
    if (filters.category && filters.category !== 'all') {
      const tagLabel = CATEGORY_TAG_MAP[filters.category];
      if (tagLabel) {
        filteredProblems = problems.filter((problem) =>
          problem.tagRelations?.some((tr) => tr.tag.label === tagLabel),
        );
      }
    }

    // Filter by search in ID (if search looks like a number)
    if (filters.search && !isNaN(Number(filters.search))) {
      const searchId = BigInt(filters.search);
      filteredProblems = filteredProblems.filter((p) => p.id === searchId);
    } else if (filters.search) {
      // Title search is handled by Prisma's contains
      filteredProblems = filteredProblems.filter((problem) =>
        problem.title.toLowerCase().includes(filters.search!.toLowerCase()),
      );
    }

    // Apply i18n translations
    let translatedProblems = filteredProblems;
    if (filteredProblems.length > 0) {
      const ids = filteredProblems.map((p) => p.id);
      const problemTranslationsMap =
        await this.i18nService.getBatchTranslations('PROBLEM', ids, locale);

      // Get all unique tag IDs
      const tagIds = [
        ...new Set(
          filteredProblems.flatMap(
            (p) =>
              p.tagRelations?.map((tr) => tr.tag?.id).filter(Boolean) || [],
          ),
        ),
      ];
      const tagTranslationsMap: Map<
        string,
        Map<string, string>
      > = tagIds.length > 0
        ? await this.i18nService.getBatchTranslations(
            'PROBLEM_TAG',
            tagIds,
            locale,
          )
        : new Map<string, Map<string, string>>();

      translatedProblems = filteredProblems.map((problem) => {
        const translations: Map<string, string> =
          problemTranslationsMap.get(String(problem.id)) ??
          new Map<string, string>();
        const translatedProblem = this.i18nService.applyTranslations(
          problem,
          translations,
          TRANSLATABLE_ENTITIES.PROBLEM.fields,
        );

        // Apply tag translations
        if ((translatedProblem as ProblemWithRelations).tagRelations) {
          (translatedProblem as ProblemWithRelations).tagRelations = (
            translatedProblem as ProblemWithRelations
          ).tagRelations!.map((tr) => {
            if (!tr.tag) return tr;
            const tagTranslations: Map<string, string> =
              tagTranslationsMap.get(tr.tag.id) ?? new Map<string, string>();
            return {
              ...tr,
              tag: this.i18nService.applyTranslations(
                tr.tag,
                tagTranslations,
                TRANSLATABLE_ENTITIES.PROBLEM_TAG.fields,
              ),
            };
          });
        }

        return translatedProblem;
      });
    }

    return {
      items: translatedProblems,
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
  }

  async findOne(
    idOrSlug: string | number,
    locale: SupportedLocale = DEFAULT_LOCALE,
  ): Promise<ProblemWithRelations | null> {
    const isNumeric = typeof idOrSlug === 'number' || !isNaN(Number(idOrSlug));

    const problem = await this.prisma.problem.findFirst({
      where: isNumeric ? { id: BigInt(idOrSlug) } : { slug: String(idOrSlug) },
      include: {
        detail: true,
        tagRelations: {
          include: {
            tag: true,
          },
        },
        languages: true,
        examples: true,
      },
    });

    if (!problem) return null;

    // Apply problem title translations
    const problemTranslations = await this.i18nService.getTranslations(
      'PROBLEM',
      problem.id,
      locale,
    );
    let translatedProblem = this.i18nService.applyTranslations(
      problem,
      problemTranslations,
      TRANSLATABLE_ENTITIES.PROBLEM.fields,
    ) as ProblemWithRelations;

    // Apply detail translations
    if (translatedProblem.detail) {
      const detailTranslations = await this.i18nService.getTranslations(
        'PROBLEM_DETAIL',
        translatedProblem.detail.id,
        locale,
      );
      const translatedDetail = this.i18nService.applyTranslations(
        translatedProblem.detail,
        detailTranslations,
        TRANSLATABLE_ENTITIES.PROBLEM_DETAIL.fields,
      );

      // Ensure JSON fields are parsed if they were translated (stringified)
      if (typeof translatedDetail.constraints_json === 'string') {
        try {
          const content =
            translatedDetail.constraints_json as unknown as string;
          translatedDetail.constraints_json = JSON.parse(content) as string[];
        } catch (_e) {
          // Keep as is if parsing fails
        }
      }

      if (
        translatedDetail.hints &&
        typeof translatedDetail.hints === 'string'
      ) {
        try {
          const content = translatedDetail.hints as unknown as string;
          translatedDetail.hints = JSON.parse(content) as string[];
        } catch (_e) {
          // Keep as is if parsing fails
        }
      }

      translatedProblem = {
        ...translatedProblem,
        detail: translatedDetail,
      };
    }

    // Apply tag translations (batch)
    if (translatedProblem.tagRelations?.length) {
      const tagIds = translatedProblem.tagRelations
        .map((tr) => tr.tag?.id)
        .filter(Boolean);
      const tagTranslationsMap = await this.i18nService.getBatchTranslations(
        'PROBLEM_TAG',
        tagIds,
        locale,
      );
      translatedProblem = {
        ...translatedProblem,
        tagRelations: translatedProblem.tagRelations.map((tr) => {
          if (!tr.tag) return tr;
          const tagTrans: Map<string, string> =
            tagTranslationsMap.get(tr.tag.id) ?? new Map<string, string>();
          return {
            ...tr,
            tag: this.i18nService.applyTranslations(
              tr.tag,
              tagTrans,
              TRANSLATABLE_ENTITIES.PROBLEM_TAG.fields,
            ),
          };
        }),
      };
    }

    // Apply example translations (batch)
    if (translatedProblem.examples?.length) {
      const exampleIds = translatedProblem.examples.map((e) => e.id);
      const exampleTranslationsMap =
        await this.i18nService.getBatchTranslations(
          'PROBLEM_EXAMPLE',
          exampleIds,
          locale,
        );
      translatedProblem = {
        ...translatedProblem,
        examples: translatedProblem.examples.map((example) => {
          const exampleTrans: Map<string, string> =
            exampleTranslationsMap.get(example.id) ?? new Map<string, string>();
          return this.i18nService.applyTranslations(
            example,
            exampleTrans,
            TRANSLATABLE_ENTITIES.PROBLEM_EXAMPLE.fields,
          );
        }),
      };
    }

    return translatedProblem;
  }

  async getRandom(): Promise<Problem | null> {
    // Get count first
    const count = await this.prisma.problem.count();
    if (count === 0) return null;

    // Get random skip value
    const skip = Math.floor(Math.random() * count);

    const result = await this.prisma.problem.findMany({
      take: 1,
      skip,
    });

    return result[0] || null;
  }

  async findAdjacent(
    id: number,
  ): Promise<{ prev: string | null; next: string | null }> {
    const prev = await this.prisma.problem.findUnique({
      where: { id: BigInt(id - 1) },
      select: { slug: true },
    });
    const next = await this.prisma.problem.findUnique({
      where: { id: BigInt(id + 1) },
      select: { slug: true },
    });

    return {
      prev: prev?.slug || null,
      next: next?.slug || null,
    };
  }

  /**
   * Find a problem with premium access check
   * Returns full problem data for non-premium or premium users
   * Returns partial data (teaser) for premium problems without access
   */
  async findOneWithPremiumCheck(
    idOrSlug: string | number,
    userId: string,
    userRole?: string,
    locale: SupportedLocale = DEFAULT_LOCALE,
  ): Promise<ProblemWithRelations | object | null> {
    const problem = await this.findOne(idOrSlug, locale);

    if (!problem) {
      return null;
    }

    // Non-premium problems are fully accessible
    if (!problem.is_premium) {
      return problem;
    }

    // Check user premium access
    const checkResult = await this.subscriptionService.hasPremiumAccess(
      userId,
      userRole,
    );

    if (checkResult.hasAccess) {
      return problem; // Full access for premium users
    }

    // Return teaser (partial data) for non-premium users
    return {
      id: problem.id,
      slug: problem.slug,
      title: problem.title,
      difficulty: problem.difficulty,
      is_premium: true,
      acceptance_rate: problem.acceptance_rate,
      // Omit: detail, examples, languages (the actual content)
    };
  }
}
