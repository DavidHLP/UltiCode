import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import HiddenTestCasesEditor from '../HiddenTestCasesEditor.vue'
import TestCaseList from '../components/TestCaseList.vue'
import { testCasesApi } from '@/api/admin/test-cases'
import type { TestCase } from '@/api/admin/test-cases'
import enTestCases from '@/i18n/locales/en-US/modules/testCases'
import zhTestCases from '@/i18n/locales/zh-CN/modules/testCases'

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
  problemId: 1,
  isSample: false,
  isHidden: true,
  testOrder: 1,
  inputText: '1\n',
  outputText: '1\n',
  explanation: '',
  createdAt: '2026-06-14T00:00:00Z',
  updatedAt: '2026-06-14T00:00:00Z',
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
    expect(testCasesApi.getTestCases).toHaveBeenCalledWith(
      'p-1',
      expect.objectContaining({ limit: 1000 }),
      expect.any(AbortSignal),
    )
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
    expect(testCasesApi.getTestCases).toHaveBeenLastCalledWith(
      'p-2',
      expect.objectContaining({ limit: 1000 }),
      expect.any(AbortSignal),
    )
  })

  it('correctly counts sample vs hidden cases from API response (XOR filter on client)', async () => {
    vi.mocked(testCasesApi.getTestCases).mockResolvedValue({
      total: 3,
      page: 1,
      limit: 1000,
      items: [
        fakeCase({ id: 'a', isSample: true, isHidden: false }),
        fakeCase({ id: 'b', isSample: false, isHidden: true }),
        fakeCase({ id: 'c', isSample: false, isHidden: true }),
      ],
    })
    const wrapper = mountEditor()
    await flushPromises()
    const html = wrapper.html()
    expect(html).toContain('1') // sample count
    expect(html).toContain('2') // hidden count
    const header = wrapper.get('.hidden-test-cases-editor > div')
    expect(header.classes()).toContain('flex-wrap')
    for (const group of header.findAll(':scope > div')) {
      expect(group.classes()).toContain('flex-wrap')
    }
    const layout = wrapper.get('test-case-list-stub').element.parentElement!
    expect(layout.classList.contains('flex-col')).toBe(true)
    expect(layout.classList.contains('md:grid')).toBe(true)
    wrapper.unmount()
  })
})

it('names each test case action menu with its visible index', () => {
  const wrapper = mount(TestCaseList, {
    props: { testCases: [fakeCase()], activeId: 'tc-1' },
    global: { plugins: [i18n] },
  })
  expect(wrapper.get('button').attributes('aria-label')).toContain('#1')
  wrapper.unmount()
})

it.each([
  ['en-US', enTestCases],
  ['zh-CN', zhTestCases],
] as const)(
  'renders literal import examples without breaking the %s dialog',
  (locale, messages) => {
    const translations = createI18n({ legacy: false, locale, messages: { [locale]: messages } })
    expect(() => translations.global.t('importPlaceholder')).not.toThrow()
    expect(translations.global.t('importPlaceholder')).toContain('{ "inputText":')
    expect(() => translations.global.t('importHelp')).not.toThrow()
    expect(translations.global.t('importHelp')).toContain('{input, output}')
  },
)
