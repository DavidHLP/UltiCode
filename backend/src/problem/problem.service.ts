import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import type {
  Problem,
  ProblemDetail,
  ProblemTag,
  Prisma,
  Difficulty,
} from '@prisma/client';
import { CATEGORY_TAG_MAP } from './constants';
import { I18nService } from '../i18n/i18n.service';
import { SupportedLocale, DEFAULT_LOCALE } from '../i18n/i18n.constants';
import { SubscriptionService } from '../subscription/subscription.service';
import { PaginatedResult } from '../contest/dto/ranking.dto';
import { CacheService } from '../cache/cache.service';

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
  /** Cache TTL in seconds */
  private static readonly CACHE_TTL_PROBLEM = 300; // 5 minutes
  private static readonly CACHE_TTL_LIST = 60; // 1 minute

  constructor(
    private readonly prisma: PrismaService,
    private readonly i18nService: I18nService,
    private readonly subscriptionService: SubscriptionService,
    private readonly cacheService: CacheService,
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

    // Generate cache key for this query
    const cacheKey = `problems:list:${locale}:${filters.category || 'all'}:${filters.difficulty || 'all'}:${filters.search || 'none'}:${page}:${limit}`;

    // Try to get from cache first (only for non-search queries)
    if (!filters.search) {
      const cached =
        await this.cacheService.get<PaginatedResult<Problem>>(cacheKey);
      if (cached) {
        return cached;
      }
    }

    // Build complete WHERE clause before querying to avoid in-memory filtering
    const where: Prisma.ProblemWhereInput = {};

    if (filters.difficulty) {
      where.difficulty = filters.difficulty as Difficulty;
    }

    // Add category filter to WHERE clause using nested tagRelations
    if (filters.category && filters.category !== 'all') {
      const tagLabel = CATEGORY_TAG_MAP[filters.category];
      if (tagLabel) {
        where.tagRelations = {
          some: {
            tag: { label: tagLabel },
          },
        };
      }
    }

    // Add search filter - handle ID search and title search
    if (filters.search) {
      if (!isNaN(Number(filters.search))) {
        // ID search
        where.id = BigInt(filters.search);
      } else {
        // Title search
        where.title = {
          contains: filters.search,
        };
      }
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

    // No in-memory filtering needed - all filters applied at database level
    const filteredProblems = problems;

    // Apply i18n translations
    let translatedProblems: ProblemWithRelations[] =
      filteredProblems as ProblemWithRelations[];
    if (filteredProblems.length > 0) {
      // Translate problems using unified method
      translatedProblems = await this.i18nService.translateEntities(
        'PROBLEM',
        filteredProblems,
        locale,
      );

      // Get all unique tag IDs and translate them
      const tagIds = [
        ...new Set(
          filteredProblems.flatMap(
            (p) =>
              p.tagRelations?.map((tr) => tr.tag?.id).filter(Boolean) || [],
          ),
        ),
      ];

      const allTags =
        tagIds.length > 0
          ? await this.prisma.problemTag.findMany({
              where: { id: { in: tagIds } },
            })
          : [];

      const translatedTags =
        tagIds.length > 0
          ? await this.i18nService.translateEntities(
              'PROBLEM_TAG',
              allTags,
              locale,
            )
          : [];

      const tagMap = new Map(translatedTags.map((t) => [t.id, t]));

      // Apply tag translations
      translatedProblems = translatedProblems.map((problem) => {
        if (!problem.tagRelations) {
          return problem;
        }
        return {
          ...problem,
          tagRelations: problem.tagRelations.map((tr) => {
            if (!tr.tag) return tr;
            const translatedTag = tagMap.get(tr.tag.id);
            return {
              ...tr,
              tag: translatedTag ?? tr.tag,
            };
          }),
        };
      });
    }

    const result = {
      items: translatedProblems,
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };

    // Cache the result (only for non-search queries)
    if (!filters.search) {
      await this.cacheService.set(
        cacheKey,
        result,
        ProblemService.CACHE_TTL_LIST,
      );
    }

    return result;
  }

  async findOne(
    idOrSlug: string | number,
    locale: SupportedLocale = DEFAULT_LOCALE,
  ): Promise<ProblemWithRelations | null> {
    const isNumeric = typeof idOrSlug === 'number' || !isNaN(Number(idOrSlug));

    // Try to get from cache first
    const cacheKey = `problem:${isNumeric ? idOrSlug : `slug:${idOrSlug}`}:${locale}`;
    const cached = await this.cacheService.get<ProblemWithRelations>(cacheKey);
    if (cached) {
      return cached;
    }

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

    // Apply problem title translations using unified method
    let translatedProblem = await this.i18nService.translateEntity(
      'PROBLEM',
      problem,
      locale,
    );

    // Apply detail translations
    if (translatedProblem.detail) {
      const translatedDetail = await this.i18nService.translateEntity(
        'PROBLEM_DETAIL',
        translatedProblem.detail,
        locale,
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

    // Apply tag translations using unified batch method
    if (translatedProblem.tagRelations?.length) {
      const tags = translatedProblem.tagRelations
        .map((tr) => tr.tag)
        .filter((tag): tag is ProblemTag => tag !== null);

      const translatedTags = await this.i18nService.translateEntities(
        'PROBLEM_TAG',
        tags,
        locale,
      );

      const tagMap = new Map(translatedTags.map((t) => [t.id, t]));

      // Filter out relations without tags, then map translated tags
      const isTagRelation = (tr: any): tr is { tag: ProblemTag } =>
        tr?.tag !== null;
      const validTagRelations =
        translatedProblem.tagRelations.filter(isTagRelation);

      translatedProblem = {
        ...translatedProblem,
        tagRelations: validTagRelations.map((tr) => ({
          ...tr,
          tag: tagMap.get(tr.tag.id) ?? tr.tag,
        })),
      };
    }

    // Apply example translations using unified batch method
    if (translatedProblem.examples?.length) {
      const translatedExamples = await this.i18nService.translateEntities(
        'PROBLEM_EXAMPLE',
        translatedProblem.examples,
        locale,
      );

      translatedProblem = {
        ...translatedProblem,
        examples: translatedExamples,
      };
    }

    // Cache the result
    const result = translatedProblem as ProblemWithRelations;
    await this.cacheService.set(
      cacheKey,
      result,
      ProblemService.CACHE_TTL_PROBLEM,
    );

    return result;
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
