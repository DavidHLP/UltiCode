import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Problem } from './problem.entity';
import { ProblemDetail } from './problem-detail.entity';
import { CATEGORY_TAG_MAP } from './constants';
import { I18nService } from '../i18n/i18n.service';
import {
  SupportedLocale,
  DEFAULT_LOCALE,
  TRANSLATABLE_ENTITIES,
} from '../i18n/i18n.constants';
import { SubscriptionService } from '../subscription/subscription.service';
import { PaginatedResult } from '../contest/dto/ranking.dto';

@Injectable()
export class ProblemService {
  constructor(
    @InjectRepository(Problem)
    private problemsRepository: Repository<Problem>,
    @InjectRepository(ProblemDetail)
    private problemDetailsRepository: Repository<ProblemDetail>,
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

    const query = this.problemsRepository
      .createQueryBuilder('problem')
      .leftJoinAndSelect('problem.tagRelations', 'tagRelations')
      .leftJoinAndSelect('tagRelations.tag', 'tag')
      .orderBy('problem.id', 'ASC')
      .skip(skip)
      .take(limit);

    if (filters.difficulty) {
      query.andWhere('problem.difficulty = :difficulty', {
        difficulty: filters.difficulty,
      });
    }

    if (filters.search) {
      query.andWhere(
        '(LOWER(problem.title) LIKE LOWER(:search) OR CAST(problem.id AS CHAR) LIKE :search)',
        { search: `%${filters.search}%` },
      );
    }

    if (filters.category && filters.category !== 'all') {
      // Map frontend category to tag labels
      const tagLabel = CATEGORY_TAG_MAP[filters.category];
      if (tagLabel) {
        const subQuery = query
          .subQuery()
          .select('relation.problem_id')
          .from('problem_tag_relations', 'relation')
          .leftJoin('relation.tag', 't')
          .where('t.label = :tagLabel')
          .getQuery();
        query.andWhere('problem.id IN ' + subQuery);
        query.setParameter('tagLabel', tagLabel);
      }
    }

    const [problems, total] = await Promise.all([
      query.getMany(),
      query.getCount(),
    ]);

    // Apply i18n translations
    let translatedProblems = problems;
    if (problems.length > 0) {
      const ids = problems.map((p) => p.id);
      const problemTranslationsMap =
        await this.i18nService.getBatchTranslations('PROBLEM', ids, locale);

      // Get all unique tag IDs
      const tagIds = [
        ...new Set(
          problems.flatMap(
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

      translatedProblems = problems.map((problem) => {
        const translations: Map<string, string> =
          problemTranslationsMap.get(String(problem.id)) ??
          new Map<string, string>();
        const translatedProblem = this.i18nService.applyTranslations(
          problem,
          translations,
          TRANSLATABLE_ENTITIES.PROBLEM.fields,
        );

        // Apply tag translations
        if (translatedProblem.tagRelations) {
          translatedProblem.tagRelations = translatedProblem.tagRelations.map(
            (tr) => {
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
            },
          );
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
  ): Promise<Problem | null> {
    let problem: Problem | null;

    if (typeof idOrSlug === 'number' || !isNaN(Number(idOrSlug))) {
      problem = await this.problemsRepository.findOne({
        where: { id: Number(idOrSlug) },
        relations: [
          'detail',
          'tagRelations',
          'tagRelations.tag',
          'languages',
          'examples',
        ],
      });
    } else {
      problem = await this.problemsRepository.findOne({
        where: { slug: idOrSlug },
        relations: [
          'detail',
          'tagRelations',
          'tagRelations.tag',
          'languages',
          'examples',
        ],
      });
    }

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
    );

    // Apply detail translations
    if (translatedProblem.detail) {
      const detailTranslations = await this.i18nService.getTranslations(
        'PROBLEM_DETAIL',
        translatedProblem.detail.id,
        locale,
      );
      translatedProblem = {
        ...translatedProblem,
        detail: this.i18nService.applyTranslations(
          translatedProblem.detail,
          detailTranslations,
          TRANSLATABLE_ENTITIES.PROBLEM_DETAIL.fields,
        ),
      };

      // Ensure JSON fields are parsed if they were translated (stringified)
      if (typeof translatedProblem.detail.constraints_json === 'string') {
        try {
          const content = translatedProblem.detail
            .constraints_json as unknown as string;
          translatedProblem.detail.constraints_json = JSON.parse(
            content,
          ) as string[];
        } catch (_e) {
          // Keep as is if parsing fails
        }
      }

      if (
        translatedProblem.detail.hints &&
        typeof translatedProblem.detail.hints === 'string'
      ) {
        try {
          const content = translatedProblem.detail.hints as unknown as string;
          translatedProblem.detail.hints = JSON.parse(content) as string[];
        } catch (_e) {
          // Keep as is if parsing fails
        }
      }
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
    const result = await this.problemsRepository
      .createQueryBuilder('problem')
      .orderBy('RAND()')
      .limit(1)
      .getOne();
    return result || null;
  }
  async findAdjacent(
    id: number,
  ): Promise<{ prev: string | null; next: string | null }> {
    const prev = await this.problemsRepository.findOne({
      where: { id: id - 1 },
      select: ['slug'],
    });
    const next = await this.problemsRepository.findOne({
      where: { id: id + 1 },
      select: ['slug'],
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
  ): Promise<Problem | object | null> {
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
