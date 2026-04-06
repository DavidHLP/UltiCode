import { useAuthStore } from "@/stores/auth";
import { apiGet, apiPost, apiPatch, apiDelete } from "@/utils/request";
import {
  EdgeOperationTargetType,
  EdgeOperationType,
  operateEdgeOperation,
} from "./edge-operations";
import type { EdgeOperationResponse } from "./edge-operations";
import type { SolutionFeedResponse, SolutionFeedItem } from "@/types/solution";
import type { ForumComment } from "@/types/forum";
export type { SolutionFeedResponse };

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
  return apiPatch<void>(`/api/solutions/${solutionId}`, data);
}

export async function deleteSolution(solutionId: string): Promise<void> {
  return apiDelete<void>(`/api/solutions/${solutionId}`);
}

export async function fetchSolution(
  solutionId: string,
): Promise<SolutionFeedItem> {
  const item = await apiGet<{
    id: string;
    problemId: number;
    userId: string;
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
    likes: number;
    dislikes?: number;
    userVote?: 0 | 1 | -1;
  }>(`/api/solutions/${solutionId}`);

  // Transform flat author fields to nested author object
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
      username: item.authorName ?? item.userId,
      name: item.authorName ?? item.userId,
      role: "",
      avatar: item.authorAvatar,
    },
    stats: {
      views: item.views ?? 0,
      comments: 0,
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

export async function fetchSolutionFeed(
  problemId: number,
  userId?: string,
): Promise<SolutionFeedResponse> {
  const url = userId
    ? `/api/problems/${problemId}/solutions?userId=${userId}`
    : `/api/problems/${problemId}/solutions`;
  const pageResult = await apiGet<{
    items: Array<{
      id: string;
      problemId: number;
      userId: string;
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
      likes: number;
      dislikes?: number;
      userVote?: 0 | 1 | -1;
    }>;
    total: number;
    page: number;
    pageSize: number;
    totalPages: number;
  }>(url);

  // Transform flat author fields to nested author object
  return {
    items: pageResult.items.map((item) => ({
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
        username: item.authorName ?? item.userId,
        name: item.authorName ?? item.userId,
        role: "",
        avatar: item.authorAvatar,
      },
      stats: item.stats,
      score: item.score,
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
    })),
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
  const response = await apiGet<
    Array<{
      id: string;
      problemId: number;
      userId: string;
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
      likes: number;
      dislikes?: number;
      userVote?: 0 | 1 | -1;
    }>
  >(`/api/solutions?${params.toString()}`);

  // Transform flat author fields to nested author object
  return {
    items: response.map((item) => ({
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
        username: item.authorName ?? item.userId,
        name: item.authorName ?? item.userId,
        role: "",
        avatar: item.authorAvatar,
      },
      stats: item.stats,
      score: item.score,
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
    })),
    total: response.length,
  };
}

export async function fetchSolutionComments(
  solutionId: string,
  userId?: string,
): Promise<ForumComment[]> {
  const url = userId
    ? `/api/solutions/${solutionId}/comments?userId=${userId}`
    : `/api/solutions/${solutionId}/comments`;
  return apiGet<ForumComment[]>(url);
}

export async function createSolutionComment(
  solutionId: string,
  content: string,
  parentId?: string,
): Promise<ForumComment> {
  const userId = useAuthStore().fetchCurrentUserId();
  if (!userId) {
    throw new Error("User must be logged in to create comments");
  }
  return apiPost<ForumComment>(`/api/solutions/${solutionId}/comments`, {
    content,
    parentId,
  });
}

export async function updateSolutionComment(
  commentId: string,
  content: string,
): Promise<ForumComment> {
  const userId = useAuthStore().fetchCurrentUserId();
  if (!userId) {
    throw new Error("User must be logged in to update comments");
  }
  return apiPatch<ForumComment>(`/api/solutions/comments/${commentId}`, {
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
