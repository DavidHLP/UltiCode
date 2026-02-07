import { apiGet, apiPost } from "@/utils/request";

export enum EdgeOperationType {
  VOTE_UP = "VOTE_UP",
  VOTE_DOWN = "VOTE_DOWN",
  ANALYZE = "ANALYZE",
}

export enum EdgeOperationTargetType {
  SOLUTION = "SOLUTION",
  SOLUTION_COMMENT = "SOLUTION_COMMENT",
  FORUM_POST = "FORUM_POST",
  FORUM_COMMENT = "FORUM_COMMENT",
  PROBLEM = "PROBLEM",
  PROBLEM_LIST = "PROBLEM_LIST",
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
  return apiPost<EdgeOperationResponse>(`/edge-operations`, {
    operationType,
    targetType,
    targetId,
  });
};
