import apiClient from '../client';

export interface ProblemList {
  id: string;
  name: string;
  description: string;
  author_id: string;
  is_public: boolean;
  is_featured: boolean;
  banner_tag?: string;
  banner_icon?: string;
  banner_theme?: string;
  banner_order: number;
  created_at: string;
  updated_at: string;
  problem_count?: number;
}

export interface ProblemListProblem {
  id: number;
  slug: string;
  title: string;
  difficulty: string;
  status: string;
  sort_order: number;
  added_at: string;
}

export interface ProblemListDetail extends ProblemList {
  problems: ProblemListProblem[];
}

export interface ProblemListQuery {
  search?: string;
  is_featured?: boolean;
  is_public?: boolean;
  page?: number;
  limit?: number;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

export interface CreateProblemListDto {
  name: string;
  description?: string;
  slug?: string;
  is_public?: boolean;
  is_featured?: boolean;
  banner_tag?: string;
  banner_icon?: string;
  banner_theme?: string;
  banner_order?: number;
  author_id?: string;
}

export interface UpdateProblemListDto {
  name?: string;
  description?: string;
  slug?: string;
  is_public?: boolean;
  is_featured?: boolean;
  banner_tag?: string;
  banner_icon?: string;
  banner_theme?: string;
  banner_order?: number;
}

export interface UpdateProblemListProblemsDto {
  problems: {
    problem_id: number;
    sort_order: number;
  }[];
}

export interface ProblemListResponse {
  data: ProblemList[];
  total: number;
  page: number;
  limit: number;
  totalPages: number;
}

export const adminProblemListsApi = {
  getLists(query: ProblemListQuery): Promise<ProblemListResponse> {
    return apiClient.get('/admin/problem-lists', { params: query });
  },

  getList(id: string): Promise<ProblemListDetail> {
    return apiClient.get(`/admin/problem-lists/${id}`);
  },

  createList(data: CreateProblemListDto): Promise<ProblemList> {
    return apiClient.post('/admin/problem-lists', data);
  },

  updateList(id: string, data: UpdateProblemListDto): Promise<ProblemList> {
    return apiClient.patch(`/admin/problem-lists/${id}`, data);
  },

  deleteList(id: string): Promise<void> {
    return apiClient.delete(`/admin/problem-lists/${id}`);
  },

  updateListProblems(id: string, data: UpdateProblemListProblemsDto): Promise<void> {
    return apiClient.post(`/admin/problem-lists/${id}/problems`, data);
  },
};
