import { apiGet } from "@/utils/request";
import type { SearchResponse, SearchQuery } from "@/types/search";

export const searchApi = {
  /**
   * Global search across all indexes
   */
  async search(params: SearchQuery): Promise<SearchResponse> {
    return apiGet<SearchResponse>("/search", { params });
  },
};
