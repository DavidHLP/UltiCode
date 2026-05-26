import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import VisibilitySection from './VisibilitySection.vue'
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
          Switch: {
            template:
              '<button data-testid="switch" :data-checked="checked" :data-disabled="disabled" @click="$emit(\'update:checked\', !checked)" @blur="$emit(\'blur\')"><slot /></button>',
            props: ['checked', 'disabled'],
            emits: ['update:checked', 'blur'],
          },
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
      const switches = wrapper.findAll('[data-testid="switch"]')
      expect(switches.length).toBe(2)
    })

    it('shows public switch checked when isPublic is true', () => {
      const wrapper = createWrapper()
      const publicSwitch = wrapper.findAll('[data-testid="switch"]')[0]
      expect(publicSwitch.attributes('data-checked')).toBe('true')
    })

    it('shows featured switch unchecked when isFeatured is false', () => {
      const wrapper = createWrapper({ modelValue: { ...mockProblemList, isFeatured: false } })
      const featuredSwitch = wrapper.findAll('[data-testid="switch"]')[1]
      expect(featuredSwitch.attributes('data-checked')).toBe('false')
    })
  })

  describe('disabled state', () => {
    it('passes disabled prop to switches', () => {
      const wrapper = createWrapper({ disabled: true })
      const switches = wrapper.findAll('[data-testid="switch"]')
      switches.forEach((sw) => {
        expect(sw.attributes('data-disabled')).toBe('true')
      })
    })
  })
})
