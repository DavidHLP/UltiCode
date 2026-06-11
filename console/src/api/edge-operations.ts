import { apiGet, apiPost } from "@/utils/request";

/**
 * Edge operation types. Must mirror backend
 * com.ulticode.modules.vote.entity.enums.EdgeOperationType.
 *
 * VOTE_UP / VOTE_DOWN / ANALYZE are the actively-used values.
 * VIEW / LIKE / DISLIKE / FAVORITE are reserved by migration
 * V20260610150000 (D-10 "per-problem reactions") for future
 * problem-detail reactions. They currently have NO frontend
 * consumer (search: `EdgeOperationType.LIKE` returns 0 hits in
 * console/ and management/); keeping them here so the FE spec
 * matches the BE contract and prevents silent runtime errors
 * if/when the D-10 feature ships. See
 * docs/edge-operations-api-test-report-2026-06-11.md §四.
 */
export enum EdgeOperationType {
  VOTE_UP = "VOTE_UP",
  VOTE_DOWN = "VOTE_DOWN",
  ANALYZE = "ANALYZE",
  // Reserved for D-10 per-problem reactions (see migration V20260610150000).
  VIEW = "VIEW",
  LIKE = "LIKE",
  DISLIKE = "DISLIKE",
  FAVORITE = "FAVORITE",
}

/**
 * Edge operation target types. Must mirror backend
 * com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType.
 *
 * POST and COMMENT are reserved for the planned generic
 * comment-system migration (no controller endpoint exists yet);
 * keeping them here prevents future drift when the comment
 * module ships. See docs/edge-operations-api-test-report-2026-06-11.md §四.
 */
export enum EdgeOperationTargetType {
  PROBLEM = "PROBLEM",
  SOLUTION = "SOLUTION",
  SOLUTION_COMMENT = "SOLUTION_COMMENT",
  FORUM_POST = "FORUM_POST",
  FORUM_COMMENT = "FORUM_COMMENT",
  PROBLEM_LIST = "PROBLEM_LIST",
  // Reserved for future generic comment system.
  POST = "POST",
  COMMENT = "COMMENT",
}

export interface EdgeOperationResponse {
  likes: number;
  dislikes: number;
  favorites: number;
  viewer: {
    vote: 1 | 0 | -1;
  };
}

export const fetchEdgeOperationStatus = async (
  targetType: EdgeOperationTargetType,
  targetId: string,
  userId?: string,
): Promise<EdgeOperationResponse> => {
  const query = userId ? `?userId=${userId}` : "";
  return apiGet<EdgeOperationResponse>(
    `/edge-operations/${targetType}/${targetId}${query}`,
  );
};

export const operateEdgeOperation = async (
  operationType: EdgeOperationType,
  targetType: EdgeOperationTargetType,
  targetId: string,
): Promise<EdgeOperationResponse> => {
  return apiPost<EdgeOperationResponse>(
    `/edge-operations`,
    {
      operationType,
      targetType,
      targetId,
    },
    { retry: 0 },
  );
};
