import { apiGet, apiPost, apiPatch, apiDelete } from "@/utils/request";
import {
  EdgeOperationTargetType,
  EdgeOperationType,
  operateEdgeOperation,
} from "./edge-operations";
import type { EdgeOperationResponse } from "./edge-operations";
import type { SolutionFeedResponse, SolutionFeedItem } from "@/types/solution";
import type { ForumComment } from "@/types/forum";
import { fetchCurrentUserId } from "@/utils/auth";
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
  return apiPost<void>(`/problems/${problemId}/solutions`, data);
}

export async function updateSolution(
  solutionId: string,
  data: CreateSolutionDto,
): Promise<void> {
  return apiPatch<void>(`/solutions/${solutionId}`, data);
}

export async function deleteSolution(solutionId: string): Promise<void> {
  return apiDelete<void>(`/solutions/${solutionId}`);
}

export async function fetchSolution(
  solutionId: string,
): Promise<SolutionFeedItem> {
  return apiGet<SolutionFeedItem>(`/solutions/${solutionId}`);
}

export async function fetchSolutionFeed(
  problemId: number,
  userId?: string,
): Promise<SolutionFeedResponse> {
  const url = userId
    ? `/problems/${problemId}/solutions?userId=${userId}`
    : `/problems/${problemId}/solutions`;
  return apiGet<SolutionFeedResponse>(url);
}

export async function fetchUserSolutions(
  userId: string,
  problemId?: string,
): Promise<SolutionFeedResponse> {
  const params = new URLSearchParams({ userId });
  if (problemId) {
    params.set("problemId", problemId);
  }
  return apiGet<SolutionFeedResponse>(`/solutions?${params.toString()}`);
}

export async function fetchSolutionComments(
  solutionId: string,
  userId?: string,
): Promise<ForumComment[]> {
  const url = userId
    ? `/solutions/${solutionId}/comments?userId=${userId}`
    : `/solutions/${solutionId}/comments`;
  return apiGet<ForumComment[]>(url);
}

export async function createSolutionComment(
  solutionId: string,
  content: string,
  parentId?: string,
): Promise<ForumComment> {
  const userId = fetchCurrentUserId();
  if (!userId) {
    throw new Error("User must be logged in to create comments");
  }
  return apiPost<ForumComment>(`/solutions/${solutionId}/comments`, {
    content,
    parentId,
  });
}

export async function updateSolutionComment(
  commentId: string,
  content: string,
): Promise<ForumComment> {
  const userId = fetchCurrentUserId();
  if (!userId) {
    throw new Error("User must be logged in to update comments");
  }
  return apiPatch<ForumComment>(`/solutions/comments/${commentId}`, {
    content,
  });
}

export async function deleteSolutionComment(commentId: string): Promise<void> {
  const userId = fetchCurrentUserId();
  if (!userId) {
    throw new Error("User must be logged in to delete comments");
  }
  return apiDelete<void>(`/solutions/comments/${commentId}`);
}

export async function voteSolution(
  solutionId: string,
  voteType: 1 | -1 | 0,
): Promise<EdgeOperationResponse> {
  if (voteType === 0) {
    throw new Error("voteType must be 1 or -1");
  }
  return operateEdgeOperation(
    voteType === 1 ? EdgeOperationType.VOTE_UP : EdgeOperationType.VOTE_DOWN,
    EdgeOperationTargetType.SOLUTION,
    solutionId,
  );
}

export async function voteSolutionComment(
  commentId: string,
  voteType: 1 | -1 | 0,
): Promise<EdgeOperationResponse> {
  if (voteType === 0) {
    throw new Error("voteType must be 1 or -1");
  }
  return operateEdgeOperation(
    voteType === 1 ? EdgeOperationType.VOTE_UP : EdgeOperationType.VOTE_DOWN,
    EdgeOperationTargetType.SOLUTION_COMMENT,
    commentId,
  );
}

export async function recordSolutionView(solutionId: string) {
  const userId = fetchCurrentUserId();
  if (!userId) return;
  return apiPost(`/views/solution/${solutionId}`, { userId });
}
