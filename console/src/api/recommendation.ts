// console/src/api/recommendation.ts
import { apiGet } from "@/utils/request";
import type {
  RecommendResponse,
  RecommendResult,
} from "@/types/recommendation";

/**
 * 推荐服务返回的数据结构（unwrap 后）
 * 注意：apiGet 已经自动 unwrap response.data，所以返回的是 data 部分
 */
type ApiResult = RecommendResponse;

/**
 * 解析推荐响应
 * 后端返回格式：{ success, code, message, data: { items, totalCount, scenario, generatedAt } }
 */
function parseRecommendResponse(response: ApiResult): RecommendResult | null {
  if (!response.success || !response.data) {
    return null;
  }
  return response.data;
}

export const recommendationApi = {
  /**
   * 获取每日推荐
   */
  async getDaily(
    size = 10,
    includeSolved = false,
  ): Promise<RecommendResult | null> {
    const params = new URLSearchParams();
    params.append("size", String(size));
    if (includeSolved) params.append("includeSolved", "true");

    const response = await apiGet<ApiResult>(
      `/recommendations/daily?${params}`,
    );
    return parseRecommendResponse(response);
  },

  /**
   * 获取薄弱点推荐
   */
  async getWeakPoints(
    size = 10,
    tags?: string[],
  ): Promise<RecommendResult | null> {
    const params = new URLSearchParams();
    params.append("size", String(size));
    if (tags && tags.length > 0) {
      params.append("tags", tags.join(","));
    }

    const response = await apiGet<ApiResult>(
      `/recommendations/weak-points?${params}`,
    );
    return parseRecommendResponse(response);
  },

  /**
   * 获取挑战模式推荐
   */
  async getChallenge(size = 5): Promise<RecommendResult | null> {
    const params = new URLSearchParams();
    params.append("size", String(size));

    const response = await apiGet<ApiResult>(
      `/recommendations/challenge?${params}`,
    );
    return parseRecommendResponse(response);
  },

  /**
   * 获取相似题目推荐
   */
  async getSimilar(
    problemId: number,
    size = 5,
  ): Promise<RecommendResult | null> {
    const params = new URLSearchParams();
    params.append("size", String(size));

    const response = await apiGet<ApiResult>(
      `/recommendations/similar/${problemId}?${params}`,
    );
    return parseRecommendResponse(response);
  },

  /**
   * 健康检查
   */
  async healthCheck(): Promise<{ status: string }> {
    return apiGet<{ status: string }>("/recommendations/health");
  },
};
