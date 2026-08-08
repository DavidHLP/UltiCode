import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import BannerSection from './BannerSection.vue'
import type { ProblemListDetail } from '@/api/admin/problem-lists'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: { value: 'en-US' },
  }),
  createI18n: () => ({
    global: {
      t: (key: string) => key,
    },
  }),
}))

vi.mock('@vueuse/core', () => ({
  useDebounceFn: (fn: (...args: unknown[]) => unknown) => {
    const debouncedFn = (...args: unknown[]) => fn(...args)
    return debouncedFn
  },
}))

vi.mock('@/utils/request', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPatch: vi.fn().mockResolvedValue(undefined),
  apiDelete: vi.fn(),
}))

vi.mock('@/api/admin/problem-lists', () => ({
  adminProblemListsApi: {
    getLists: vi.fn(),
    getList: vi.fn(),
    createList: vi.fn(),
    updateList: vi.fn(),
    deleteList: vi.fn(),
    updateListProblems: vi.fn(),
    updateBasicInfo: vi.fn(),
    updateVisibility: vi.fn(),
    updateBanner: vi.fn().mockResolvedValue(undefined),
  },
}))

vi.mock('@/components/ui/form')
vi.mock('@/components/ui/input')
vi.mock('@/components/ui/select')

describe('BannerSection', () => {
  const mockProblemList: ProblemListDetail = {
    id: 'test-id-123',
    name: 'Test Problem List',
    description: 'Test Description',
    authorId: 'author-1',
    isPublic: true,
    isFeatured: true,
    bannerTag: 'Featured',
    bannerTheme: 'blue',
    bannerOrder: 1,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    problemCount: 10,
    problems: [],
  }

  const createWrapper = (
    props: { modelValue?: ProblemListDetail | null; disabled?: boolean } = {},
  ) => {
    return mount(BannerSection, {
      props: {
        modelValue: props.modelValue ?? mockProblemList,
        disabled: props.disabled ?? false,
      },
      global: {
        stubs: {
          FormField: true,
          FormItem: true,
          FormLabel: true,
          FormControl: true,
          FormMessage: true,
          FormDescription: true,
          Input: true,
          Select: true,
          SelectContent: true,
          SelectItem: true,
          SelectTrigger: true,
          SelectValue: true,
        },
      },
    })
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('rendering', () => {
    it('renders banner section', () => {
      const wrapper = createWrapper()
      expect(wrapper.exists()).toBe(true)
    })
  })
})
