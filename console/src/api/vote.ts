import {
  EdgeOperationTargetType,
  EdgeOperationType,
  operateEdgeOperation,
} from "@/api/edge-operations";
import type { EdgeOperationResponse } from "@/api/edge-operations";

export { EdgeOperationTargetType as VoteTargetType };

export interface VoteResponse extends EdgeOperationResponse {
  userVote: 0 | 1 | -1;
}

export const vote = async (
  targetType: EdgeOperationTargetType,
  targetId: string,
  voteType: 1 | -1,
): Promise<VoteResponse> => {
  const operationType =
    voteType === 1 ? EdgeOperationType.VOTE_UP : EdgeOperationType.VOTE_DOWN;
  const res = await operateEdgeOperation(operationType, targetType, targetId);
  return {
    ...res,
    userVote: res.viewer.vote,
  };
};
