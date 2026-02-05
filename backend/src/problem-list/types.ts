import { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma.service';

// ============================================================================
// Types
// ============================================================================

export interface ProblemListSummary {
  id: string;
  name: string;
  description?: string;
  authorId: string;
  isPublic: boolean;
  isFeatured: boolean;
  bannerTag?: string;
  bannerIcon?: string;
  bannerTheme?: string;
  bannerOrder?: number;
  createdAt: Date;
  updatedAt: Date;
  problemCount: number;
  favoritesCount: number;
  isSaved?: boolean;
  categoryId?: string;
}

export interface CategorySummary {
  id: string;
  name: string;
  sortOrder: number;
  lists: ProblemListSummary[];
}

export interface UserProblemListsResponse {
  myLists: ProblemListSummary[];
  savedLists: ProblemListSummary[];
  featured: ProblemListSummary[];
  categories: CategorySummary[];
}

export interface ProblemListDetailResponse {
  list: ProblemListSummary | null;
  problems: ProblemListProblem[];
  stats: ProblemListStats | null;
  viewer?: {
    isSaved: boolean;
    categoryId: string | null;
  };
  categories?: Array<{
    id: string;
    name: string;
    sortOrder: number;
  }>;
}

export interface ProblemListStats {
  listId: string;
  totalCount: number;
  solvedCount: number;
  attemptedCount: number;
  todoCount: number;
  progress: number;
}

export interface ProblemListProblem {
  id: number;
  slug: string;
  title: string;
  difficulty: string;
  acceptanceRate: number;
  status: string;
  isPremium: boolean;
  hasSolution: boolean;
  completedTime?: Date | null;
  tags: string[];
}

// Helper function to convert Prisma ProblemList to TypeORM-compatible format
export function convertProblemListFromPrisma(
  list: Prisma.ProblemListGetPayload<Record<string, never>>,
): ProblemListSummary {
  return {
    id: list.id,
    name: list.name,
    description: list.description ?? undefined,
    authorId: list.author_id,
    isPublic: list.is_public,
    isFeatured: list.is_featured,
    bannerTag: list.banner_tag ?? undefined,
    bannerIcon: list.banner_icon ?? undefined,
    bannerTheme: list.banner_theme ?? undefined,
    bannerOrder: list.banner_order ?? undefined,
    createdAt: list.created_at,
    updatedAt: list.updated_at,
    problemCount: 0,
    favoritesCount: 0,
  };
}

// Transaction client type for use in services
export type PrismaClient = Prisma.TransactionClient | PrismaService;
