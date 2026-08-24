import { useAuthStore } from "@/stores/auth";
import { apiGet, apiPost, apiPatch, apiPut, apiDelete } from "@/utils/request";
import type { SolutionFeedResponse, SolutionFeedItem } from "@/types/solution";
import type { SolutionComment } from "@/types/comment";
export type { SolutionFeedResponse };

/** Backend API response shape for a single solution (detail). */
interface SolutionApiItem {
  id: string;
  problemId: number;
  userId: string;
  authorUsername?: string;
  authorName?: string;
  authorAvatar?: string;
  title: string;
  summary: string;
  highlight?: string;
  flair?: string;
  badges?: string[];
  language: string;
  languageFilter?: string;
  topicName?: string;
  topicTranslated?: string;
  topic?: { id: string; name: string; translatedName?: string };
  stats: { views: number; comments: number; likes: number; dislikes: number };
  score: number;
  isPinned?: boolean;
  isLocked?: boolean;
  createdAt: string;
  publishedAt: string;
  content: string;
  tags: string[];
  votes: number;
  views: number;
  comments?: number;
  likes: number;
  dislikes?: number;
  userVote?: 0 | 1 | -1;
}

/** Backend API response shape for solution list items (lightweight, no content). */
interface SolutionListApiItem {
  id: string;
  problemId: number;
  title: string;
  summary: string;
  language: string;
  tags: string[];
  author?: {
    id: string;
    username?: string;
    name: string;
    avatar?: string;
  };
  counts?: { views: number; comments: number; likes: number; dislikes: number };
  score: number;
  viewerVote?: 0 | 1 | -1;
  publishedAt: string;
  isPinned?: boolean;
}

/** Transform a raw API solution item into the frontend SolutionFeedItem shape. */
function transformApiSolution(item: SolutionApiItem): SolutionFeedItem {
  return {
    id: item.id,
    problem_id: item.problemId?.toString() ?? "",
    title: item.title,
    summary: item.summary,
    highlight: item.highlight,
    flair: item.flair,
    badges: item.badges,
    authorId: item.userId,
    author: {
      id: item.userId,
      username: item.authorUsername ?? "",
      name: item.authorName ?? item.userId,
      role: "",
      avatar: item.authorAvatar,
    },
    stats: {
      views: item.views ?? 0,
      comments: item.comments ?? 0,
      likes: item.likes ?? 0,
      dislikes: item.dislikes ?? 0,
    },
    score: item.score ?? 0,
    is_pinned: item.isPinned,
    is_locked: item.isLocked,
    created_at: item.createdAt,
    publishedAt: item.publishedAt,
    topicName: item.topicName,
    topicTranslated: item.topicTranslated,
    topic: item.topic,
    language: item.language,
    languageFilter: item.languageFilter,
    content: item.content,
    tags: item.tags,
    votes: item.votes,
    views: item.views,
    likes: item.likes,
    dislikes: item.dislikes,
    userVote: item.userVote,
  };
}

/** Transform a lightweight list API item into the frontend SolutionFeedItem shape. */
function transformListApiSolution(item: SolutionListApiItem): SolutionFeedItem {
  const author = item.author;
  const counts = item.counts;
  return {
    id: item.id,
    problem_id: item.problemId?.toString() ?? "",
    title: item.title,
    summary: item.summary,
    authorId: author?.id ?? "",
    author: {
      id: author?.id ?? "",
      username: author?.username ?? "",
      name: author?.name ?? "",
      role: "",
      avatar: author?.avatar,
    },
    stats: {
      views: counts?.views ?? 0,
      comments: counts?.comments ?? 0,
      likes: counts?.likes ?? 0,
      dislikes: counts?.dislikes ?? 0,
    },
    score: item.score ?? 0,
    is_pinned: item.isPinned,
    created_at: item.publishedAt,
    publishedAt: item.publishedAt,
    language: item.language,
    languageFilter: "all",
    // content omitted — loaded on-demand via fetchSolution
    tags: item.tags ?? [],
    votes: 0,
    views: counts?.views ?? 0,
    likes: counts?.likes ?? 0,
    dislikes: counts?.dislikes ?? 0,
    userVote: item.viewerVote ?? 0,
  };
}

export interface CreateSolutionDto {
  title: string;
  content: string;
  language: string;
  tags?: string[];
}

export async function createSolution(
  problemId: string,
  data: CreateSolutionDto,
): Promise<void> {
  return apiPost<void>(`/api/problems/${problemId}/solutions`, data);
}

export async function updateSolution(
  solutionId: string,
  data: CreateSolutionDto,
): Promise<void> {
  return apiPut<void>(`/api/solutions/${solutionId}`, data);
}

export async function deleteSolution(solutionId: string): Promise<void> {
  return apiDelete<void>(`/api/solutions/${solutionId}`);
}

export async function fetchSolution(
  solutionId: string,
  userId?: string,
): Promise<SolutionFeedItem> {
  const url = userId
    ? `/api/solutions/${solutionId}?userId=${userId}`
    : `/api/solutions/${solutionId}`;
  const item = await apiGet<SolutionApiItem>(url);
  return transformApiSolution(item);
}

export async function fetchSolutionFeed(
  problemId: number,
): Promise<SolutionFeedResponse> {
  const pageResult = await apiGet<{
    items: SolutionListApiItem[];
    total: number;
    page: number;
    pageSize: number;
    totalPages: number;
  }>(`/api/problems/${problemId}/solutions`);

  return {
    items: pageResult.items.map(transformListApiSolution),
    total: pageResult.total,
  };
}

export async function fetchUserSolutions(
  userId: string,
  problemId?: string,
): Promise<SolutionFeedResponse> {
  const params = new URLSearchParams({ userId });
  if (problemId) {
    params.set("problemId", problemId);
  }
  const response = await apiGet<SolutionApiItem[]>(
    `/api/solutions?${params.toString()}`,
  );

  return {
    items: response.map(transformApiSolution),
    total: response.length,
  };
}

export async function fetchSolutionComments(
  solutionId: string,
): Promise<SolutionComment[]> {
  if (!solutionId || solutionId.trim() === "") {
    throw new TypeError("fetchSolutionComments: solutionId is required");
  }
  return apiGet<SolutionComment[]>(`/api/solutions/${solutionId}/comments`);
}

export async function createSolutionComment(
  solutionId: string,
  content: string,
  parentId?: string,
): Promise<SolutionComment> {
  const userId = useAuthStore().fetchCurrentUserId();
  if (!userId) {
    throw new Error("User must be logged in to create comments");
  }
  return apiPost<SolutionComment>(`/api/solutions/${solutionId}/comments`, {
    content,
    parentId,
  });
}

export async function updateSolutionComment(
  commentId: string,
  content: string,
): Promise<SolutionComment> {
  const userId = useAuthStore().fetchCurrentUserId();
  if (!userId) {
    throw new Error("User must be logged in to update comments");
  }
  return apiPatch<SolutionComment>(`/api/solutions/comments/${commentId}`, {
    content,
  });
}

export async function deleteSolutionComment(commentId: string): Promise<void> {
  const userId = useAuthStore().fetchCurrentUserId();
  if (!userId) {
    throw new Error("User must be logged in to delete comments");
  }
  return apiDelete<void>(`/api/solutions/comments/${commentId}`);
}

export async function recordSolutionView(solutionId: string) {
  const userId = useAuthStore().fetchCurrentUserId();
  if (!userId) return;
  return apiPost(`/api/views/solution/${solutionId}`, { userId });
}
