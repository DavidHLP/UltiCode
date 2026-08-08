import { apiDelete, apiGet, apiPatch, apiPost } from "@/utils/request";
import type {
  ForumComment,
  ForumCommunity,
  ForumCommunityRule,
  ForumCommunityLink,
  ForumPost,
  ForumThread,
  ForumTag,
  PageResult,
  ForumPostStats,
  ForumFlairType,
} from "@/types/forum";

// =========================================================================
// Normalizer: ensures all post fields have correct types
// (backend may return stats/media/tags as JSON strings in some queries)
// =========================================================================

function normalizeStats(raw: unknown): ForumPostStats | undefined {
  if (raw && typeof raw === "object") return raw as ForumPostStats;
  if (typeof raw === "string") {
    try {
      return JSON.parse(raw) as ForumPostStats;
    } catch {
      return undefined;
    }
  }
  return undefined;
}

function normalizeNumber(raw: unknown): number | undefined {
  if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  if (typeof raw === "string" && raw.trim() !== "") {
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
}

function normalizeTags(raw: unknown): string[] {
  if (Array.isArray(raw)) return raw as string[];
  if (typeof raw === "string") {
    try {
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }
  return [];
}

function normalizeMedia(raw: unknown): unknown {
  if (raw && typeof raw === "object") return raw;
  if (typeof raw === "string") {
    try {
      return JSON.parse(raw);
    } catch {
      return undefined;
    }
  }
  return undefined;
}

function normalizePost(
  raw: Record<string, unknown> & {
    userId?: string;
    authorUsername?: string;
    authorAvatar?: string;
    communityId?: string;
    communityName?: string;
    communitySlug?: string;
    flairType?: string;
    flairLabel?: string;
  },
): ForumPost {
  const stats = normalizeStats(raw.stats);
  const commentCount = normalizeNumber(raw.commentCount);

  return {
    ...raw,
    author: {
      id: raw.userId ?? "",
      username: raw.authorUsername ?? raw.userId ?? "",
      avatar: raw.authorAvatar,
    },
    community: raw.communityId
      ? {
          id: raw.communityId,
          name: raw.communityName ?? "",
          slug: raw.communitySlug ?? "",
        }
      : undefined,
    flair: raw.flairType
      ? { type: raw.flairType as ForumFlairType, text: raw.flairLabel }
      : undefined,
    stats:
      commentCount === undefined
        ? stats
        : {
            ...(stats ?? {}),
            comments: commentCount,
          },
    tags: normalizeTags(raw.tags),
    media: normalizeMedia(raw.media),
  } as ForumPost;
}

// =========================================================================
// API functions
// =========================================================================

export async function fetchForumPosts(options?: {
  sortBy?: string;
  page?: number;
  pageSize?: number;
}): Promise<{ posts: ForumPost[]; total: number; totalPages: number }> {
  const params: Record<string, string | number> = {};
  if (options?.sortBy) params.sortBy = options.sortBy;
  if (options?.page) params.page = options.page;
  if (options?.pageSize) params.pageSize = options.pageSize;

  const response = await apiGet<
    PageResult<
      {
        userId: string;
        authorUsername?: string;
        authorAvatar?: string;
        communityId?: string;
        communityName?: string;
        communitySlug?: string;
        flairType?: string;
        flairLabel?: string;
      } & Record<string, unknown>
    >
  >("/forum/posts", { params });

  const rows = Array.isArray(response) ? response : response.items;
  const total = !Array.isArray(response) ? response.total : rows.length;
  const totalPages = !Array.isArray(response) ? response.totalPages : 1;

  return {
    posts: rows.map(normalizePost),
    total,
    totalPages,
  };
}

export async function fetchForumPost(postId: string): Promise<ForumPost> {
  const raw = await apiGet<
    {
      userId: string;
      authorUsername?: string;
      authorAvatar?: string;
      communityId?: string;
      communityName?: string;
      communitySlug?: string;
      flairType?: string;
      flairLabel?: string;
    } & Record<string, unknown>
  >(`/forum/posts/${postId}`);

  return normalizePost(raw);
}

export async function fetchForumCommunities(options?: {
  featured?: boolean;
}): Promise<ForumCommunity[]> {
  return apiGet<ForumCommunity[]>("/forum/communities", {
    params: options?.featured ? { featured: "true" } : undefined,
  });
}

export async function fetchForumCommunity(slugOrId: string): Promise<{
  community: ForumCommunity | null;
  rules: ForumCommunityRule[];
  links: ForumCommunityLink[];
}> {
  return apiGet(`/forum/communities/${slugOrId}`);
}

export async function fetchCommunityPosts(
  slug: string,
  options?: { sortBy?: string; page?: number; pageSize?: number },
): Promise<{ posts: ForumPost[]; total: number; totalPages: number }> {
  const params: Record<string, string | number> = {};
  if (options?.sortBy) params.sortBy = options.sortBy;
  if (options?.page) params.page = options.page;
  if (options?.pageSize) params.pageSize = options.pageSize;

  const response = await apiGet<
    PageResult<
      {
        userId: string;
        authorUsername?: string;
        authorAvatar?: string;
        communityId?: string;
        communityName?: string;
        communitySlug?: string;
        flairType?: string;
        flairLabel?: string;
      } & Record<string, unknown>
    >
  >(`/forum/communities/${slug}/posts`, { params });

  const rows = Array.isArray(response) ? response : response.items;
  const total = !Array.isArray(response) ? response.total : rows.length;
  const totalPages = !Array.isArray(response) ? response.totalPages : 1;

  return {
    posts: rows.map(normalizePost),
    total,
    totalPages,
  };
}

export async function fetchForumTags(): Promise<ForumTag[]> {
  return apiGet<ForumTag[]>("/forum/tags");
}

export async function fetchForumQuickFilters(): Promise<
  Array<{ label: string; value: string }>
> {
  return apiGet<Array<{ label: string; value: string }>>(
    "/forum/quick-filters",
  );
}

export async function fetchForumThread(postId: string): Promise<ForumThread> {
  const response = await apiGet<{
    post: {
      userId: string;
      authorUsername: string;
      authorAvatar: string;
      communityId?: string;
      communityName?: string;
      communitySlug?: string;
      flairType?: string;
      flairLabel?: string;
    } & Record<string, unknown>;
    comments: ForumComment[];
  }>(`/forum/posts/${postId}/thread`);

  const normalized = normalizePost(response.post);
  return {
    ...normalized,
    comments: response.comments ?? [],
  };
}

export async function createForumComment(
  postId: string,
  body: string,
  parentId?: string | null,
): Promise<void> {
  await apiPost<unknown>(`/forum/posts/${postId}/comments`, {
    body,
    parentId: parentId ?? null,
  });
}

export async function updateForumComment(
  commentId: string,
  body: string,
): Promise<void> {
  await apiPatch(`/forum/comments/${commentId}`, { body });
}

export async function deleteForumComment(commentId: string): Promise<void> {
  await apiDelete(`/forum/comments/${commentId}`);
}

export async function recordForumView(postId: string) {
  return apiPost(`/forum/posts/${postId}/view`, {}, { skipErrorHandler: true });
}

export async function recordForumShare(postId: string) {
  return apiPost(
    `/forum/posts/${postId}/share`,
    {},
    { skipErrorHandler: true },
  );
}

export async function joinForumCommunity(id: string): Promise<void> {
  await apiPost(`/forum/communities/${id}/join`);
}

export async function leaveForumCommunity(id: string): Promise<void> {
  await apiPost(`/forum/communities/${id}/leave`);
}

export async function createForumPost(input: {
  title: string;
  excerpt: string;
  body?: string;
  communityId: string;
  tags?: string[];
  flairType?: string | null;
  flairLabel?: string | null;
  media?: unknown[];
}): Promise<ForumPost> {
  const raw = await apiPost<Record<string, unknown>>("/forum/posts", input);
  return normalizePost(raw as Parameters<typeof normalizePost>[0]);
}

export async function updateForumPost(
  postId: string,
  input: Partial<{
    title: string;
    excerpt: string;
    body?: string;
    tags: string[];
    flairType: string | null;
    flairLabel: string | null;
    isPinned: boolean;
    isLocked: boolean;
    media?: unknown[];
  }>,
): Promise<ForumPost> {
  const raw = await apiPatch<Record<string, unknown>>(
    `/forum/posts/${postId}`,
    input,
  );
  return normalizePost(raw as Parameters<typeof normalizePost>[0]);
}

export async function deleteForumPost(postId: string): Promise<void> {
  await apiDelete(`/forum/posts/${postId}`);
}

export async function fetchMyForumPosts(options?: {
  page?: number;
  pageSize?: number;
}): Promise<{ posts: ForumPost[]; total: number; totalPages: number }> {
  const params: Record<string, string | number> = {};
  if (options?.page) params.page = options.page;
  if (options?.pageSize) params.pageSize = options.pageSize;

  const response = await apiGet<
    PageResult<
      {
        userId: string;
        authorUsername?: string;
        authorAvatar?: string;
        communityId?: string;
        communityName?: string;
        communitySlug?: string;
        flairType?: string;
        flairLabel?: string;
      } & Record<string, unknown>
    >
  >("/forum/me/posts", { params });

  const rows = Array.isArray(response) ? response : response.items;
  const total = !Array.isArray(response) ? response.total : rows.length;
  const totalPages = !Array.isArray(response) ? response.totalPages : 1;

  return {
    posts: rows.map(normalizePost),
    total,
    totalPages,
  };
}
