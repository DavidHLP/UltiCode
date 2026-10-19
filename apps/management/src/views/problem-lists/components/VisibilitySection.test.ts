import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import VisibilitySection from './VisibilitySection.vue'
import type { ProblemListDetail } from '@/api/admin/problem-lists'
import { apiPatch } from '@/utils/request'

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

vi.mock('@vueuse/core', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@vueuse/core')>()),
  useDebounceFn: (fn: (...args: unknown[]) => unknown) => {
    const debouncedFn = (...args: unknown[]) => {
      debouncedFn.cancel?.()
      return fn(...args)
    }
    debouncedFn.cancel = vi.fn()
    return debouncedFn
  },
}))

vi.mock('@/utils/request', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPatch: vi.fn().mockResolvedValue(undefined),
  apiDelete: vi.fn(),
}))

vi.mock('vue-sonner', () => ({
  toast: {
    error: vi.fn(),
    warning: vi.fn(),
  },
}))

describe('VisibilitySection', () => {
  const mockProblemList: ProblemListDetail = {
    id: 'test-id-123',
    name: 'Test Problem List',
    description: 'Test Description',
    authorId: 'author-1',
    isPublic: true,
    isFeatured: false,
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
    return mount(VisibilitySection, {
      props: {
        modelValue: props.modelValue ?? mockProblemList,
        disabled: props.disabled ?? false,
      },
      global: {
        stubs: {
          TooltipProvider: true,
          Tooltip: true,
          TooltipContent: true,
          TooltipTrigger: true,
        },
      },
    })
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('rendering', () => {
    it('renders switches', () => {
      const wrapper = createWrapper()
      const switches = wrapper.findAll('[role="switch"]')
      expect(switches.length).toBe(2)
    })

    it('shows public switch checked when isPublic is true', () => {
      const wrapper = createWrapper()
      const publicSwitch = wrapper.findAll('[role="switch"]')[0]
      expect(publicSwitch.attributes('aria-checked')).toBe('true')
    })

    it('shows featured switch unchecked when isFeatured is false', () => {
      const wrapper = createWrapper({ modelValue: { ...mockProblemList, isFeatured: false } })
      const featuredSwitch = wrapper.findAll('[role="switch"]')[1]
      expect(featuredSwitch.attributes('aria-checked')).toBe('false')
    })
  })

  it.each([
    ['isPublic', 0, false],
    ['isFeatured', 1, true],
  ] as const)(
    'forwards a real %s switch through the existing autosave contract',
    async (field, index, value) => {
      vi.mocked(apiPatch).mockResolvedValue({ ...mockProblemList, [field]: value })
      const wrapper = createWrapper()
      await wrapper.findAll('[role="switch"]')[index].trigger('click')
      await flushPromises()
      expect(wrapper.emitted('update:modelValue')?.[0][0]).toMatchObject({ [field]: value })
      expect(apiPatch).toHaveBeenCalledWith(
        `/admin/problem-lists/${mockProblemList.id}/visibility`,
        {
          isPublic: field === 'isPublic' ? value : mockProblemList.isPublic,
          isFeatured: field === 'isFeatured' ? value : mockProblemList.isFeatured,
        },
      )
      wrapper.unmount()
    },
  )

  describe('disabled state', () => {
    it('passes disabled prop to switches', () => {
      const wrapper = createWrapper({ disabled: true })
      const switches = wrapper.findAll('[role="switch"]')
      switches.forEach((sw) => {
        expect((sw.element as HTMLButtonElement).disabled).toBe(true)
      })
    })
  })
})
