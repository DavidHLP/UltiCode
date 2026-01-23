import { apiGet, apiPost } from "@/utils/request";
import {
  EdgeOperationTargetType,
  EdgeOperationType,
  operateEdgeOperation,
  fetchEdgeOperationStatus,
} from "@/api/edge-operations";
import type { EdgeOperationResponse } from "@/api/edge-operations";

export {
  EdgeOperationTargetType,
  EdgeOperationType,
  operateEdgeOperation,
  fetchEdgeOperationStatus,
};
export type { EdgeOperationResponse };

export async function fetchProblemNote(
  problemId: number,
): Promise<{ content: string } | null> {
  return apiGet<{ content: string } | null>(`/problems/${problemId}/note`);
}

export async function saveProblemNote(
  problemId: number,
  content: string,
): Promise<{ content: string }> {
  return apiPost<{ content: string }>(`/problems/${problemId}/note`, {
    content,
  });
}
