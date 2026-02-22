import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { MeiliSearch, Index } from 'meilisearch';
import { PrismaService } from '../prisma.service';
import {
  SearchQueryDto,
  SearchIndex,
  SearchResponse,
  SearchResult,
} from './dto/search-query.dto';

interface ProblemSearchDoc {
  id: string;
  title: string;
  slug: string;
  difficulty: string;
  tags: string[];
  summary?: string;
}

interface UserSearchDoc {
  id: string;
  username: string;
  name?: string;
  bio?: string;
}

interface PostSearchDoc {
  id: string;
  title: string;
  content: string;
  communityName: string;
  authorName: string;
}

interface SolutionSearchDoc {
  id: string;
  title: string;
  content: string;
  problemTitle: string;
  authorName: string;
}

@Injectable()
export class SearchService implements OnModuleInit {
  private readonly logger = new Logger(SearchService.name);
  private client: MeiliSearch;
  private enabled = false;

  constructor(
    private configService: ConfigService,
    private prisma: PrismaService,
  ) {
    const host = this.configService.get<string>('MEILISEARCH_HOST');
    const apiKey = this.configService.get<string>('MEILISEARCH_API_KEY');

    if (host) {
      this.client = new MeiliSearch({ host, apiKey });
      this.enabled = true;
      this.logger.log(`MeiliSearch client initialized: ${host}`);
    } else {
      this.logger.warn(
        'MeiliSearch host not configured. Search will use database fallback.',
      );
    }
  }

  async onModuleInit(): Promise<void> {
    if (!this.enabled) return;

    try {
      // Create indexes if they don't exist
      await this.setupIndexes();
      this.logger.log('MeiliSearch indexes setup complete');
    } catch (error) {
      this.logger.error(`Failed to setup MeiliSearch indexes: ${error}`);
    }
  }

  private async setupIndexes(): Promise<void> {
    const indexes = [
      { name: SearchIndex.PROBLEMS, primaryKey: 'id' },
      { name: SearchIndex.USERS, primaryKey: 'id' },
      { name: SearchIndex.POSTS, primaryKey: 'id' },
      { name: SearchIndex.SOLUTIONS, primaryKey: 'id' },
    ];

    for (const { name, primaryKey } of indexes) {
      try {
        await this.client.createIndex(name, { primaryKey });
      } catch {
        // Index already exists
      }

      // Configure searchable attributes
      const index = this.client.index(name);
      await this.configureIndex(index, name);
    }
  }

  private async configureIndex(index: Index, name: string): Promise<void> {
    switch (name) {
      case SearchIndex.PROBLEMS:
        await index.updateSearchableAttributes(['title', 'summary', 'tags']);
        await index.updateFilterableAttributes(['difficulty']);
        await index.updateRankingRules([
          'words',
          'typo',
          'proximity',
          'attribute',
          'sort',
          'exactness',
        ]);
        break;
      case SearchIndex.USERS:
        await index.updateSearchableAttributes(['username', 'name', 'bio']);
        break;
      case SearchIndex.POSTS:
        await index.updateSearchableAttributes(['title', 'content']);
        await index.updateFilterableAttributes(['communityName']);
        break;
      case SearchIndex.SOLUTIONS:
        await index.updateSearchableAttributes(['title', 'content']);
        break;
    }
  }

  async search(dto: SearchQueryDto): Promise<SearchResponse> {
    const { query, index } = dto;
    const page = dto.page ?? 1;
    const limit = dto.limit ?? 20;
    const offset = (page - 1) * limit;

    if (this.enabled) {
      return this.searchWithMeili(query, index, limit, offset, page);
    }

    return this.searchWithDatabase(query, index, limit, offset, page);
  }

  private async searchWithMeili(
    query: string,
    index: SearchIndex | undefined,
    limit: number,
    offset: number,
    page: number,
  ): Promise<SearchResponse> {
    const results: SearchResult[] = [];
    let total = 0;

    const indexesToSearch = index ? [index] : Object.values(SearchIndex);

    for (const idx of indexesToSearch) {
      try {
        const searchResult = await this.client
          .index(idx)
          .search(query, { limit, offset, attributesToHighlight: ['*'] });

        total += searchResult.estimatedTotalHits ?? 0;

        for (const hit of searchResult.hits) {
          results.push(this.mapHitToResult(hit, idx, searchResult.hits));
        }
      } catch (error) {
        this.logger.warn(`Search failed for index ${idx}: ${error}`);
      }
    }

    return {
      query,
      total,
      page,
      limit,
      results: results.slice(0, limit),
    };
  }

  private mapHitToResult(
    hit: Record<string, unknown>,
    index: SearchIndex,
    _allHits: unknown[],
  ): SearchResult {
    const formatted = hit._formatted as Record<string, string> | undefined;

    switch (index) {
      case SearchIndex.PROBLEMS:
        return {
          id: hit.id as string,
          type: index,
          title: (hit.title as string) || '',
          description: (hit.summary as string) || '',
          url: `/problems/${hit.slug}`,
          highlights: formatted
            ? {
                title: [formatted.title || ''],
                summary: [formatted.summary || ''],
              }
            : undefined,
        };
      case SearchIndex.USERS:
        return {
          id: hit.id as string,
          type: index,
          title: (hit.username as string) || '',
          description: (hit.name as string) || '',
          url: `/users/${hit.id}`,
        };
      case SearchIndex.POSTS:
        return {
          id: hit.id as string,
          type: index,
          title: (hit.title as string) || '',
          description: ((hit.content as string) || '').slice(0, 200),
          url: `/forum/posts/${hit.id}`,
        };
      case SearchIndex.SOLUTIONS:
        return {
          id: hit.id as string,
          type: index,
          title: (hit.title as string) || '',
          description: ((hit.content as string) || '').slice(0, 200),
          url: `/solutions/${hit.id}`,
        };
      default:
        return {
          id: hit.id as string,
          type: index,
          title: String(hit.id),
          url: '/',
        };
    }
  }

  private async searchWithDatabase(
    query: string,
    index: SearchIndex | undefined,
    limit: number,
    offset: number,
    page: number,
  ): Promise<SearchResponse> {
    const results: SearchResult[] = [];
    let total = 0;

    // Search problems
    if (!index || index === SearchIndex.PROBLEMS) {
      const problems = await this.prisma.problem.findMany({
        where: {
          is_published: true,
          is_deleted: false,
          OR: [{ title: { contains: query } }, { slug: { contains: query } }],
        },
        include: { detail: true, tagRelations: { include: { tag: true } } },
        take: limit,
        skip: offset,
      });

      const problemCount = await this.prisma.problem.count({
        where: {
          is_published: true,
          is_deleted: false,
          OR: [{ title: { contains: query } }, { slug: { contains: query } }],
        },
      });

      total += problemCount;

      for (const problem of problems) {
        results.push({
          id: problem.id.toString(),
          type: SearchIndex.PROBLEMS,
          title: problem.title,
          description: problem.detail?.summary?.slice(0, 200) || '',
          url: `/problems/${problem.slug}`,
        });
      }
    }

    // Search users
    if (!index || index === SearchIndex.USERS) {
      const users = await this.prisma.user.findMany({
        where: {
          OR: [
            { username: { contains: query } },
            { name: { contains: query } },
          ],
        },
        take: limit,
        skip: offset,
      });

      const userCount = await this.prisma.user.count({
        where: {
          OR: [
            { username: { contains: query } },
            { name: { contains: query } },
          ],
        },
      });

      total += userCount;

      for (const user of users) {
        results.push({
          id: user.id,
          type: SearchIndex.USERS,
          title: user.username,
          description: user.name || '',
          url: `/users/${user.id}`,
        });
      }
    }

    return {
      query,
      total,
      page,
      limit,
      results: results.slice(0, limit),
    };
  }

  // Indexing methods
  async indexProblem(problem: {
    id: bigint;
    title: string;
    slug: string;
    difficulty: string;
    summary?: string;
    tags: string[];
  }): Promise<void> {
    if (!this.enabled) return;

    const doc: ProblemSearchDoc = {
      id: problem.id.toString(),
      title: problem.title,
      slug: problem.slug,
      difficulty: problem.difficulty,
      summary: problem.summary,
      tags: problem.tags,
    };

    await this.client.index(SearchIndex.PROBLEMS).addDocuments([doc]);
  }

  async indexUser(user: {
    id: string;
    username: string;
    name?: string;
    bio?: string;
  }): Promise<void> {
    if (!this.enabled) return;

    const doc: UserSearchDoc = {
      id: user.id,
      username: user.username,
      name: user.name,
      bio: user.bio,
    };

    await this.client.index(SearchIndex.USERS).addDocuments([doc]);
  }

  async deleteProblem(id: string): Promise<void> {
    if (!this.enabled) return;
    await this.client.index(SearchIndex.PROBLEMS).deleteDocument(id);
  }

  async deleteUser(id: string): Promise<void> {
    if (!this.enabled) return;
    await this.client.index(SearchIndex.USERS).deleteDocument(id);
  }
}
