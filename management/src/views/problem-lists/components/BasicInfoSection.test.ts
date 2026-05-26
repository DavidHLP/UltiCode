import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import BasicInfoSection from './BasicInfoSection.vue'
import type { ProblemListDetail } from '@/api/admin/problem-lists'

if (typeof globalThis.document === 'undefined') {
  globalThis.document = window.document
}

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

vi.mock('vue-sonner', () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
}))

vi.mock('@/api/admin/problem-lists', async () => {
  const actual = await vi.importActual('@/api/admin/problem-lists')
  return {
    ...actual,
    adminProblemListsApi: {
      updateBasicInfo: vi.fn().mockResolvedValue(undefined),
      createList: vi.fn().mockResolvedValue({ id: 'new-list-id', name: '', description: '' }),
    },
  }
})

vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual('@vueuse/core')
  return {
    ...actual,
    watchDebounced: vi.fn(() => ({ stop: vi.fn() })),
  }
})

function createMockProblemList(overrides: Partial<ProblemListDetail> = {}): ProblemListDetail {
  return {
    id: 'list-1',
    name: 'Test List',
    description: 'Test Description',
    authorId: 'author-1',
    isPublic: true,
    isFeatured: false,
    bannerTag: undefined,
    bannerIcon: undefined,
    bannerTheme: 'blue',
    bannerOrder: 0,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    problemCount: 0,
    problems: [],
    ...overrides,
  }
}

function mountBasicInfoSection(props = {}) {
  return mount(BasicInfoSection, {
    props: {
      modelValue: null,
      disabled: false,
      ...props,
    },
    global: {
      stubs: {
        FormField: {
          template: '<div><slot :componentField="{}" /></div>',
        },
        FormItem: {
          template: '<div><slot /></div>',
        },
        FormLabel: {
          template: '<label><slot /></label>',
        },
        FormControl: {
          template: '<div><slot /></div>',
        },
        FormMessage: {
          template: '<span><slot /></span>',
        },
        Input: {
          props: ['modelValue', 'disabled'],
          template: '<input :value="modelValue" :disabled="disabled" data-testid="input-name" />',
        },
        Textarea: {
          props: ['modelValue', 'disabled'],
          template:
            '<textarea :value="modelValue" :disabled="disabled" data-testid="textarea-description" />',
        },
        Button: {
          template: '<button><slot /></button>',
        },
      },
    },
    attachTo: document.body,
  })
}

describe('BasicInfoSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('rendering', () => {
    it('renders name and description fields', async () => {
      const wrapper = mountBasicInfoSection({
        modelValue: createMockProblemList(),
      })
      await flushPromises()

      expect(wrapper.find('[data-testid="input-name"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="textarea-description"]').exists()).toBe(true)
    })

    it('displays section comment', async () => {
      const wrapper = mountBasicInfoSection({
        modelValue: createMockProblemList(),
      })
      await flushPromises()

      expect(wrapper.text()).toContain('problemLists.sections.basicInfo')
    })
  })

  describe('props handling', () => {
    it('renders with modelValue data', async () => {
      const problemList = createMockProblemList({
        name: 'My Custom List',
        description: 'My custom description',
      })
      const wrapper = mountBasicInfoSection({
        modelValue: problemList,
      })
      await flushPromises()

      const inputEl = wrapper.find('[data-testid="input-name"]').element as HTMLInputElement
      const textareaEl = wrapper.find('[data-testid="textarea-description"]')
        .element as HTMLTextAreaElement

      expect(inputEl.value).toBe('My Custom List')
      expect(textareaEl.value).toBe('My custom description')
    })

    it('handles null modelValue gracefully', async () => {
      const wrapper = mountBasicInfoSection({
        modelValue: null,
      })
      await flushPromises()

      expect(wrapper.find('[data-testid="input-name"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="textarea-description"]').exists()).toBe(true)
    })

    it('disables inputs when disabled prop is true', async () => {
      const wrapper = mountBasicInfoSection({
        modelValue: createMockProblemList(),
        disabled: true,
      })
      await flushPromises()

      const inputEl = wrapper.find('[data-testid="input-name"]').element as HTMLInputElement
      const textareaEl = wrapper.find('[data-testid="textarea-description"]')
        .element as HTMLTextAreaElement

      expect(inputEl.disabled).toBe(true)
      expect(textareaEl.disabled).toBe(true)
    })
  })

  describe('auto-save', () => {
    it('exposes saveStatus via defineExpose', async () => {
      const wrapper = mountBasicInfoSection({
        modelValue: createMockProblemList(),
      })
      await flushPromises()

      expect(wrapper.vm.saveStatus).toBeDefined()
      expect(['idle', 'saving', 'saved', 'error']).toContain(wrapper.vm.saveStatus)
    })
  })
})
