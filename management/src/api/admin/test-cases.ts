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
 * Convert an ({@code isSample}, {@code isHidden}) flag pair into the canonical
 * {@link CaseScope}. Returns SAMPLE for legacy/undefined pairs so existing
 * test case rows stay queryable in the UI.
 */
export function mapFlagsToCaseScope(
  // isSample is accepted for call-site symmetry with mapCaseScopeToFlags; scope
  // is derived from isHidden alone so legacy null/is_sample=false rows resolve
  // to SAMPLE (leading underscore marks it intentionally unused).
  _isSample: boolean | null | undefined,
  isHidden: boolean | null | undefined,
): CaseScope {
  if (isHidden === true) {
    return 'HIDDEN'
  }
  // isHidden === false | null | undefined, treat anything non-hidden as SAMPLE
  return 'SAMPLE'
}

/**
 * Convert a {@link CaseScope} into the ({@code isSample}, {@code isHidden})
 * flag pair to send to the backend. Always emits both booleans so the wire
 * contract satisfies the backend XOR filter.
 */
export function mapCaseScopeToFlags(scope: CaseScope): { isSample: boolean; isHidden: boolean } {
  switch (scope) {
    case 'HIDDEN':
      return { isSample: false, isHidden: true }
    case 'SAMPLE':
    default:
      return { isSample: true, isHidden: false }
  }
}

/**
 * True when the given flag pair is well-formed (isSample XOR isHidden).
 *
 * <p>Used by the bulk import parser and any other free-form input path
 * to reject ambiguous dto candidates before they reach the backend.
 * The backend will reject the same pairs at validation, but catching
 * them client-side gives a useful error message and avoids a
 * round-trip.
 *
 * <p>This is the local canonical truth &mdash; {@link mapFlagsToCaseScope}
 * deliberately tolerates legacy null-flag rows; this helper does not.
 */
export function isValidCaseScopeFlags(flags: {
  isSample: boolean
  isHidden: boolean
}): boolean {
  return flags.isSample !== flags.isHidden
}
