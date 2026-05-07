import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { h, ref } from 'vue'
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

const FormFieldStub = {
  props: ['name'],
  setup(props: { name: string }, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => slots.default?.({
      componentField: {
        name: props.name,
        modelValue: '',
        'onUpdate:modelValue': () => {},
        onBlur: () => {},
      },
      value: '',
      handleChange: () => {},
      handleBlur: () => {},
      errorMessage: '',
    })
  },
}

const FormItemStub = {
  setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => slots.default?.()
  },
}

const FormLabelStub = {
  setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => slots.default?.()
  },
}

const FormControlStub = {
  setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => slots.default?.()
  },
}

const FormMessageStub = {
  setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => slots.default?.() || null
  },
}

const InputStub = {
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue', 'blur'],
  setup(props: { modelValue?: string; disabled?: boolean }, { emit }: { emit: (event: string, value?: unknown) => void }) {
    return () =>
      h('input', {
        'data-testid': 'input-name',
        value: props.modelValue || '',
        disabled: props.disabled,
        onInput: (e: Event) => emit('update:modelValue', (e.target as HTMLInputElement).value),
        onBlur: () => emit('blur'),
      })
  },
}

const TextareaStub = {
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue', 'blur'],
  setup(props: { modelValue?: string; disabled?: boolean }, { emit }: { emit: (event: string, value?: unknown) => void }) {
    return () =>
      h('textarea', {
        'data-testid': 'textarea-description',
        value: props.modelValue || '',
        disabled: props.disabled,
        onInput: (e: Event) => emit('update:modelValue', (e.target as HTMLTextAreaElement).value),
        onBlur: () => emit('blur'),
      })
  },
}

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
        FormField: FormFieldStub,
        FormItem: FormItemStub,
        FormLabel: FormLabelStub,
        FormControl: FormControlStub,
        FormMessage: FormMessageStub,
        Input: InputStub,
        Textarea: TextareaStub,
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
      const textareaEl = wrapper.find('[data-testid="textarea-description"]').element as HTMLTextAreaElement

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
      const textareaEl = wrapper.find('[data-testid="textarea-description"]').element as HTMLTextAreaElement

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

    it('emits update:modelValue when saveChanges is called', async () => {
      const problemList = createMockProblemList()
      const wrapper = mountBasicInfoSection({
        modelValue: problemList,
      })
      await flushPromises()

      const { adminProblemListsApi } = await import('@/api/admin/problem-lists')
      ;(adminProblemListsApi.updateBasicInfo as ReturnType<typeof vi.fn>).mockResolvedValueOnce(undefined)

      wrapper.vm.form.setFieldValue('name', 'Changed Name')
      await flushPromises()

      await wrapper.vm.saveChanges()
      await flushPromises()

      expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    })
  })

  describe('validation', () => {
    it('validates name is required', async () => {
      const wrapper = mountBasicInfoSection({
        modelValue: createMockProblemList({ name: '' }),
      })
      await flushPromises()

      const { adminProblemListsApi } = await import('@/api/admin/problem-lists')
      ;(adminProblemListsApi.updateBasicInfo as ReturnType<typeof vi.fn>).mockResolvedValueOnce(undefined)

      await wrapper.vm.saveChanges()
      await flushPromises()

      expect(adminProblemListsApi.updateBasicInfo).not.toHaveBeenCalled()
    })
  })
})
