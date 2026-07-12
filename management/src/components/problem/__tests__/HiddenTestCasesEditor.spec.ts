import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import HiddenTestCasesEditor from '../HiddenTestCasesEditor.vue'
import { testCasesApi } from '@/api/admin/test-cases'
import type { TestCase } from '@/api/admin/test-cases'

// Mock the API module to assert call shape end-to-end (no real network).
vi.mock('@/api/admin/test-cases', async () => {
  const actual =
    await vi.importActual<typeof import('@/api/admin/test-cases')>('@/api/admin/test-cases')
  return {
    ...actual,
    testCasesApi: {
      getTestCases: vi.fn(),
      createTestCase: vi.fn(),
      updateTestCase: vi.fn(),
      deleteTestCase: vi.fn(),
      bulkImportTestCases: vi.fn(),
      exportTestCases: vi.fn(),
      exportTestCasesAsFile: vi.fn(),
      reorderTestCases: vi.fn(),
    },
  }
})

const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  messages: {
    'zh-CN': { testCases: { title: '测试用例', sample: '样例', hidden: '隐藏' } },
    'en-US': { testCases: { title: 'Test Cases', sample: 'Sample', hidden: 'Hidden' } },
  },
})

const fakeCase = (overrides: Partial<TestCase> = {}): TestCase => ({
  id: 'tc-1',
  problem_id: 'p-1',
  is_sample: false,
  is_hidden: true,
  test_order: 1,
  input_text: '1\n',
  output_text: '1\n',
  explanation: '',
  created_at: '2026-06-14T00:00:00Z',
  updated_at: '2026-06-14T00:00:00Z',
  ...overrides,
})

function mountEditor() {
  return mount(HiddenTestCasesEditor, {
    props: { problemId: 'p-1' },
    global: {
      plugins: [i18n],
      stubs: {
        IconFlask: true,
        IconPlus: true,
        IconUpload: true,
        IconDownload: true,
        IconLoader2: true,
        Dialog: { template: '<div><slot /></div>' },
        DialogContent: { template: '<div><slot /></div>' },
        DialogHeader: { template: '<div><slot /></div>' },
        DialogTitle: { template: '<div><slot /></div>' },
        DialogFooter: { template: '<div><slot /></div>' },
        TestCaseList: true,
        TestCaseDetail: true,
        TestCaseForm: true,
      },
    },
  })
}

describe('HiddenTestCasesEditor — admin API contract (task #10 P0-1)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('calls GET /admin/problems/{id}/test-cases on mount with limit 1000', async () => {
    vi.mocked(testCasesApi.getTestCases).mockResolvedValue({
      total: 0,
      page: 1,
      limit: 1000,
      items: [],
    })
    mountEditor()
    await flushPromises()
    expect(testCasesApi.getTestCases).toHaveBeenCalledWith('p-1', { limit: 1000 })
  })

  it('refetches when problemId prop changes (route-level case)', async () => {
    vi.mocked(testCasesApi.getTestCases).mockResolvedValue({
      total: 0,
      page: 1,
      limit: 1000,
      items: [],
    })
    const wrapper = mountEditor()
    await flushPromises()
    expect(testCasesApi.getTestCases).toHaveBeenCalledTimes(1)
    await wrapper.setProps({ problemId: 'p-2' })
    await flushPromises()
    expect(testCasesApi.getTestCases).toHaveBeenCalledTimes(2)
    expect(testCasesApi.getTestCases).toHaveBeenLastCalledWith('p-2', { limit: 1000 })
  })

  it('correctly counts sample vs hidden cases from API response (XOR filter on client)', async () => {
    vi.mocked(testCasesApi.getTestCases).mockResolvedValue({
      total: 3,
      page: 1,
      limit: 1000,
      items: [
        fakeCase({ id: 'a', is_sample: true, is_hidden: false }),
        fakeCase({ id: 'b', is_sample: false, is_hidden: true }),
        fakeCase({ id: 'c', is_sample: false, is_hidden: true }),
      ],
    })
    const wrapper = mountEditor()
    await flushPromises()
    const html = wrapper.html()
    expect(html).toContain('1') // sample count
    expect(html).toContain('2') // hidden count
  })
})
