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
  async getTestCases(problemId: string, params?: TestCaseQueryParams): Promise<TestCasesResponse> {
    const response = await apiGet<TestCasesResponse>(`/admin/problems/${problemId}/test-cases`, {
      params,
    })
    return response
  },

  async getTestCase(problemId: string, testCaseId: string): Promise<TestCase> {
    const response = await apiGet<TestCase>(`/admin/problems/${problemId}/test-cases/${testCaseId}`)
    return response
  },

  async createTestCase(problemId: string, data: CreateTestCaseDto): Promise<TestCase> {
    const response = await apiPost<TestCase>(`/admin/problems/${problemId}/test-cases`, data)
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
    const response = await apiGet<TestCase[]>(`/admin/problems/${problemId}/test-cases/export`)
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

  async reorderTestCases(problemId: string, testCaseIds: string[]): Promise<{ success: boolean }> {
    const response = await apiPut<{ success: boolean }>(
      `/admin/problems/${problemId}/test-cases/reorder`,
      { testCaseIds },
    )
    return response
  },
}

/**
 * Canonical per-case scope enum mirroring backend {@code CaseScope}
 * (com.ulticode.modules.submission.enums.CaseScope).
 *
 * Two durable values map onto the {@code test_cases.is_sample} / {@code is_hidden}
 * columns. The frontend never persists a third value (DRAFT is intentionally
 * absent — see task #10 plan, P9 拍板 2026-06-14 16:38):
 *   - SAMPLE: is_sample=true, is_hidden=false  (public example shown in statement)
 *   - HIDDEN: is_sample=false, is_hidden=true  (private judge case, admin-only)
 *
 * Legacy rows written before P0-1 may carry {@code is_sample=null} /
 * {@code is_hidden=null}; {@link mapFlagsToCaseScope} treats that as SAMPLE
 * for backward compatibility. {@link mapCaseScopeToFlags} always emits both
 * flags explicitly, so XOR always holds on the wire to the backend.
 */
export type CaseScope = 'SAMPLE' | 'HIDDEN'

/**
 * Convert a ({@code is_sample}, {@code is_hidden}) flag pair into the canonical
 * {@link CaseScope}. Returns SAMPLE for legacy/undefined pairs so existing
 * test case rows stay queryable in the UI.
 */
export function mapFlagsToCaseScope(
  isSample: boolean | null | undefined,
  isHidden: boolean | null | undefined,
): CaseScope {
  if (isHidden === true) {
    return 'HIDDEN'
  }
  // isHidden === false | null | undefined, treat anything non-hidden as SAMPLE
  return 'SAMPLE'
}

/**
 * Convert a {@link CaseScope} into the ({@code is_sample}, {@code is_hidden})
 * flag pair to send to the backend. Always emits both booleans so the wire
 * contract satisfies the backend XOR filter.
 */
export function mapCaseScopeToFlags(scope: CaseScope): { is_sample: boolean; is_hidden: boolean } {
  switch (scope) {
    case 'HIDDEN':
      return { is_sample: false, is_hidden: true }
    case 'SAMPLE':
    default:
      return { is_sample: true, is_hidden: false }
  }
}
