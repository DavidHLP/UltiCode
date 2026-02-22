import { apiGet, apiPost, apiPut, apiDelete, apiDownload } from '@/utils/request'

export interface TestCase {
  id: string
  problem_id: string
  is_sample: boolean
  is_hidden: boolean
  test_order: number
  input_text: string
  output_text: string
  explanation?: string
  constraints?: Record<string, unknown>
  created_at: string
  updated_at: string
}

export interface TestCaseQueryParams {
  is_sample?: boolean
  is_hidden?: boolean
  page?: number
  limit?: number
}

export interface TestCasesResponse {
  total: number
  page: number
  limit: number
  items: TestCase[]
}

export interface CreateTestCaseDto {
  is_sample?: boolean
  is_hidden?: boolean
  test_order?: number
  input_text: string
  output_text: string
  explanation?: string
  constraints?: Record<string, unknown>
}

export interface UpdateTestCaseDto {
  is_sample?: boolean
  is_hidden?: boolean
  test_order?: number
  input_text?: string
  output_text?: string
  explanation?: string
  constraints?: Record<string, unknown>
}

export interface BulkImportTestCaseDto {
  input_text: string
  output_text: string
  is_sample?: boolean
  is_hidden?: boolean
  explanation?: string
}

export interface BulkImportTestCasesDto {
  replace_existing?: boolean
  test_cases: BulkImportTestCaseDto[]
}

export interface BulkImportResponse {
  count: number
}

export const testCasesApi = {
  async getTestCases(
    problemId: string,
    params?: TestCaseQueryParams,
  ): Promise<TestCasesResponse> {
    const response = await apiGet<TestCasesResponse>(
      `/admin/problems/${problemId}/test-cases`,
      { params },
    )
    return response
  },

  async getTestCase(problemId: string, testCaseId: string): Promise<TestCase> {
    const response = await apiGet<TestCase>(
      `/admin/problems/${problemId}/test-cases/${testCaseId}`,
    )
    return response
  },

  async createTestCase(problemId: string, data: CreateTestCaseDto): Promise<TestCase> {
    const response = await apiPost<TestCase>(
      `/admin/problems/${problemId}/test-cases`,
      data,
    )
    return response
  },

  async updateTestCase(
    problemId: string,
    testCaseId: string,
    data: UpdateTestCaseDto,
  ): Promise<TestCase> {
    const response = await apiPut<TestCase>(
      `/admin/problems/${problemId}/test-cases/${testCaseId}`,
      data,
    )
    return response
  },

  async deleteTestCase(problemId: string, testCaseId: string): Promise<void> {
    await apiDelete(`/admin/problems/${problemId}/test-cases/${testCaseId}`)
  },

  async exportTestCases(problemId: string): Promise<TestCase[]> {
    const response = await apiGet<TestCase[]>(
      `/admin/problems/${problemId}/test-cases/export`,
    )
    return response
  },

  async exportTestCasesAsFile(problemId: string): Promise<void> {
    const date = new Date().toISOString().split('T')[0]
    await apiDownload(
      `/admin/problems/${problemId}/test-cases/export`,
      `test-cases-${problemId}-${date}.json`,
    )
  },

  async bulkImportTestCases(
    problemId: string,
    data: BulkImportTestCasesDto,
  ): Promise<BulkImportResponse> {
    const response = await apiPost<BulkImportResponse>(
      `/admin/problems/${problemId}/test-cases/bulk`,
      data,
    )
    return response
  },

  async reorderTestCases(
    problemId: string,
    testCaseIds: string[],
  ): Promise<{ success: boolean }> {
    const response = await apiPut<{ success: boolean }>(
      `/admin/problems/${problemId}/test-cases/reorder`,
      { testCaseIds },
    )
    return response
  },
}
