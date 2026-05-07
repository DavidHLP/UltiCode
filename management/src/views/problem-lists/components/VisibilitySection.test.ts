import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { h } from 'vue'
import VisibilitySection from './VisibilitySection.vue'
import type { ProblemList } from '@/api/admin/problem-lists'

const { SwitchStub, TooltipStub, TooltipContentStub, TooltipProviderStub, TooltipTriggerStub } = vi.hoisted(() => ({
  SwitchStub: {
    props: ['checked', 'disabled'],
    emits: ['update:checked', 'blur'],
    setup(props: { checked?: boolean; disabled?: boolean }, { emit }: { emit: (event: string, ...args: unknown[]) => void }) {
      return () =>
        h('button', {
          'data-testid': 'switch',
          'data-checked': props.checked,
          'data-disabled': props.disabled,
          onClick: () => emit('update:checked', !props.checked),
          onBlur: () => emit('blur'),
        })
    },
  },
  TooltipProviderStub: {
    setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
      return () => slots.default?.()
    },
  },
  TooltipStub: {
    setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
      return () => slots.default?.()
    },
  },
  TooltipTriggerStub: {
    props: ['asChild'],
    setup(props: { asChild?: boolean }, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
      return () => (props.asChild ? slots.default?.() : h('span', {}, slots.default?.()))
    },
  },
  TooltipContentStub: {
    props: ['class'],
    setup(props: { class?: string }, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
      return () => h('div', { class: props.class }, slots.default?.())
    },
  },
}))

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

vi.mock('@/components/ui/switch', () => ({
  Switch: SwitchStub,
}))

vi.mock('@/components/ui/tooltip', async () => {
  const actual = await vi.importActual('@/components/ui/tooltip')
  return {
    ...actual as Record<string, unknown>,
    Tooltip: TooltipStub,
    TooltipContent: TooltipContentStub,
    TooltipProvider: TooltipProviderStub,
    TooltipTrigger: TooltipTriggerStub,
  }
})

describe('VisibilitySection', () => {
  const mockProblemList: ProblemList = {
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
  }

  const createWrapper = (props: { modelValue?: ProblemList | null; disabled?: boolean } = {}) => {
    return mount(VisibilitySection, {
      props: {
        modelValue: props.modelValue ?? mockProblemList,
        disabled: props.disabled ?? false,
      },
      global: {
        stubs: {
          Switch: SwitchStub,
          Tooltip: TooltipStub,
          TooltipContent: TooltipContentStub,
          TooltipProvider: TooltipProviderStub,
          TooltipTrigger: TooltipTriggerStub,
        },
      },
    })
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('rendering', () => {
    it('renders both switches', () => {
      const wrapper = createWrapper()
      const switches = wrapper.findAll('[data-testid="switch"]')
      expect(switches.length).toBe(2)
    })

    it('displays isPublic as checked when modelValue.isPublic is true', () => {
      const wrapper = createWrapper()
      const publicSwitch = wrapper.findAll('[data-testid="switch"]')[0]
      expect(publicSwitch.attributes('data-checked')).toBe('true')
    })

    it('displays isFeatured as unchecked when modelValue.isFeatured is false', () => {
      const wrapper = createWrapper({ modelValue: { ...mockProblemList, isFeatured: false } })
      const featuredSwitch = wrapper.findAll('[data-testid="switch"]')[1]
      expect(featuredSwitch.attributes('data-checked')).toBe('false')
    })

    it('renders save status indicator container', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('.space-y-4').exists()).toBe(true)
    })

    it('shows tooltip trigger when isFeatured is true', () => {
      const wrapper = createWrapper({ modelValue: { ...mockProblemList, isFeatured: true } })
      const tooltipProvider = wrapper.findComponent(TooltipProviderStub)
      expect(tooltipProvider.exists()).toBe(true)
    })
  })

  describe('auto-save', () => {
    it('calls updateVisibility when switch changes', async () => {
      const { apiPatch } = await import('@/utils/request')
      const wrapper = createWrapper()
      const publicSwitch = wrapper.findAll('[data-testid="switch"]')[0]

      await publicSwitch.trigger('click')
      await flushPromises()

      expect(apiPatch).toHaveBeenCalled()
    })

    it('triggers save on blur', async () => {
      const { apiPatch } = await import('@/utils/request')
      const wrapper = createWrapper()
      const publicSwitch = wrapper.findAll('[data-testid="switch"]')[0]

      await publicSwitch.trigger('blur')
      await flushPromises()

      expect(apiPatch).toHaveBeenCalled()
    })

    it('emits update:modelValue when switch changes', async () => {
      const wrapper = createWrapper()
      const publicSwitch = wrapper.findAll('[data-testid="switch"]')[0]

      await publicSwitch.trigger('click')
      await flushPromises()

      const updateEvents = wrapper.emitted('update:modelValue')
      expect(updateEvents).toBeTruthy()
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

  describe('props', () => {
    it('accepts null modelValue', () => {
      const wrapper = createWrapper({ modelValue: null })
      expect(wrapper.exists()).toBe(true)
    })
  })
})
