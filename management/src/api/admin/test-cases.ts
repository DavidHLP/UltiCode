import { apiGet, apiPost, apiPut, apiDelete, apiDownload } from '@/utils/request'

/**
 * Test-case wire contract.
 *
 * The backend serves camelCase JSON (Spring Boot default Jackson, no
 * snake_case naming strategy — see JacksonConfig + shared/domain-types). These
 * DTO shapes mirror {@code com.ulticode.modules.problem.entity.TestCase} and
 * the admin DTOs field-for-field so a request/response mismatch is a
 * compile-time error, not a runtime 400.
 */
export interface TestCase {
  id: string
  problemId: number
  isSample: boolean
  isHidden: boolean
  testOrder: number
  inputText: string
  outputText: string
  inputs?: string
  explanation?: string
  constraints?: string
  createdAt: string
  updatedAt: string
}

export interface TestCaseQueryParams {
  isSample?: boolean
  isHidden?: boolean
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
  isSample?: boolean
  isHidden?: boolean
  testOrder?: number
  inputText: string
  outputText: string
  explanation?: string
  constraints?: string
}

export interface UpdateTestCaseDto {
  isSample?: boolean
  isHidden?: boolean
  testOrder?: number
  inputText?: string
  outputText?: string
  explanation?: string
  constraints?: string
}

/**
 * Wire contract for a single test case in a bulk-import request. Mirrors the
 * backend {@code CreateTestCaseDTO} (the service reuses it for each entry)
 * and the focused {@code model/testCaseImport.NormalizedTestCaseImport}
 * shape, with {@code isSample} / {@code isHidden} already canonicalised by
 * the import normalizer.
 */
export interface BulkImportTestCaseDto {
  inputText: string
  outputText: string
  isSample?: boolean
  isHidden?: boolean
  explanation?: string
}

export interface BulkImportTestCasesDto {
  replaceExisting?: boolean
  testCases: BulkImportTestCaseDto[]
}

export interface BulkImportResponse {
  count: number
}

export const testCasesApi = {
  async getTestCases(problemId: string, params?: TestCaseQueryParams, signal?: AbortSignal): Promise<TestCasesResponse> {
    return await apiGet<TestCasesResponse>(`/admin/problems/${problemId}/test-cases`, {
      params,
      signal,
    })
  },

  async getTestCase(problemId: string, testCaseId: string): Promise<TestCase> {
    return await apiGet<TestCase>(`/admin/problems/${problemId}/test-cases/${testCaseId}`)
  },

  async createTestCase(problemId: string, data: CreateTestCaseDto): Promise<TestCase> {
    return await apiPost<TestCase>(`/admin/problems/${problemId}/test-cases`, data)
  },

  async updateTestCase(
    problemId: string,
    testCaseId: string,
    data: UpdateTestCaseDto,
  ): Promise<TestCase> {
    return await apiPut<TestCase>(`/admin/problems/${problemId}/test-cases/${testCaseId}`, data)
  },

  async deleteTestCase(problemId: string, testCaseId: string): Promise<void> {
    await apiDelete(`/admin/problems/${problemId}/test-cases/${testCaseId}`)
  },

  /**
   * Download every test case for a problem as a JSON file. The endpoint returns
   * an octet-stream (not the {@code Result<T>} envelope), so it must go through
   * {@link apiDownload} rather than {@link apiGet}.
   */
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
    signal?: AbortSignal,
  ): Promise<BulkImportResponse> {
    return await apiPost<BulkImportResponse>(`/admin/problems/${problemId}/test-cases/bulk`, data, { signal })
  },

  async reorderTestCases(problemId: string, testCaseIds: string[], signal?: AbortSignal): Promise<void> {
    await apiPut<void>(`/admin/problems/${problemId}/test-cases/reorder`, testCaseIds, { signal })
  },
}
