import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick, ref, h } from 'vue'
import DescriptionForm from '../DescriptionForm.vue'
import { Difficulty, ProblemStatus } from '@/api/admin/problems'
import type { ProblemDescriptionFormData } from '@/lib/schemas/problemDescription'

if (typeof globalThis.document === 'undefined') {
  // @ts-expect-error - polyfill for environments without document
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

const mockFetchAllTags = vi.fn().mockResolvedValue([])

vi.mock('@/stores/admin/problems', () => ({
  useProblemsStore: () => ({
    allTags: ref([]),
    tagsLoading: ref(false),
    fetchAllTags: mockFetchAllTags,
  }),
}))

vi.mock('@/components/ui/form', async () => {
  const { h, inject, provide } = await import('vue')
  const { FormContextKey } = await import('vee-validate')

  const FormFieldStub = {
    props: ['name'],
    setup(
      props: { name: string },
      { slots }: { slots: Record<string, (...args: unknown[]) => unknown> },
    ) {
      const form = inject(FormContextKey)
      if (!form) {
        return () => null
      }

      const handleChange = (val: unknown) => {
        form.setFieldValue(props.name, val)
      }

      const handleBlur = () => {
        form.setFieldTouched(props.name, true)
      }

      provide('formFieldError', () => form.errors.value[props.name])

      return () => {
        const value = form.values[props.name]
        const errorMessage = form.errors.value[props.name]

        const componentField = {
          modelValue: value,
          'onUpdate:modelValue': handleChange,
          onBlur: handleBlur,
          name: props.name,
        }
        return h('div', { 'data-testid': `form-field-${props.name}` }, [
          slots.default?.({
            componentField,
            value,
            handleChange,
            handleBlur,
            errorMessage,
          }),
          errorMessage ? h('span', { class: 'form-error-msg' }, errorMessage) : null,
        ])
      }
    },
  }

  const FormItemStub = {
    setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
      return () => h('div', { 'data-slot': 'form-item' }, slots.default?.())
    },
  }

  const FormLabelStub = {
    setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
      return () => h('label', { 'data-slot': 'form-label' }, slots.default?.())
    },
  }

  const FormControlStub = {
    setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
      return () => slots.default?.() || null
    },
  }

  const FormMessageStub = {
    setup() {
      const getErrorMessage = inject('formFieldError', () => '')
      return () => {
        const errorMessage =
          typeof getErrorMessage === 'function' ? getErrorMessage() : getErrorMessage
        return errorMessage
          ? h('p', { 'data-slot': 'form-message', class: 'text-destructive' }, errorMessage)
          : null
      }
    },
  }

  return {
    FormField: FormFieldStub,
    FormItem: FormItemStub,
    FormLabel: FormLabelStub,
    FormControl: FormControlStub,
    FormMessage: FormMessageStub,
    FORM_ITEM_INJECTION_KEY: Symbol('form-item'),
  }
})

const MarkdownEditorStub = {
  props: ['modelValue', 'placeholder'],
  emits: ['update:modelValue'],
  setup(props: { modelValue: string }, { emit }: { emit: (event: string, value: string) => void }) {
    return () =>
      h('textarea', {
        'data-testid': 'markdown-editor',
        value: props.modelValue ?? '',
        onInput: (e: Event) => emit('update:modelValue', (e.target as HTMLTextAreaElement).value),
      })
  },
}

const LivePreviewPanelStub = {
  props: ['data'],
  setup() {
    return () => h('div', { 'data-testid': 'live-preview' }, 'LivePreviewPanel')
  },
}

const TagsSelectorStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  setup(
    props: { modelValue: string[] },
    { emit }: { emit: (event: string, value: string[]) => void },
  ) {
    return () =>
      h('div', { 'data-testid': 'tags-selector' }, [
        h(
          'button',
          {
            'data-testid': 'add-tag-btn',
            onClick: () => emit('update:modelValue', [...(props.modelValue || []), 'tag-1']),
          },
          'Add Tag',
        ),
      ])
  },
}

const ExamplesEditorStub = {
  props: ['name'],
  setup() {
    return () => h('div', { 'data-testid': 'examples-editor' }, 'ExamplesEditor')
  },
}

const ConstraintsEditorStub = {
  props: ['name'],
  setup() {
    return () => h('div', { 'data-testid': 'constraints-editor' }, 'ConstraintsEditor')
  },
}

const HintsEditorStub = {
  props: ['name'],
  setup() {
    return () => h('div', { 'data-testid': 'hints-editor' }, 'HintsEditor')
  },
}

const SelectStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  setup(
    props: { modelValue?: string },
    {
      emit,
      slots,
    }: {
      emit: (event: string, value: string) => void
      slots: Record<string, (...args: unknown[]) => unknown>
    },
  ) {
    return () =>
      h(
        'select',
        {
          'data-testid': 'select-stub',
          value: props.modelValue || '',
          onChange: (e: Event) => emit('update:modelValue', (e.target as HTMLSelectElement).value),
        },
        slots.default?.(),
      )
  },
}

const SelectTriggerStub = {
  setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => slots.default?.() || null
  },
}

const SelectValueStub = {
  setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => slots.default?.() || null
  },
}

const SelectContentStub = {
  setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => slots.default?.() || null
  },
}

const SelectItemStub = {
  props: ['value'],
  setup(
    props: { value: string },
    { slots }: { slots: Record<string, (...args: unknown[]) => unknown> },
  ) {
    return () => h('option', { value: props.value }, slots.default?.())
  },
}

const CheckboxStub = {
  props: ['checked'],
  emits: ['update:checked'],
  setup(props: { checked?: boolean }, { emit }: { emit: (event: string, value: boolean) => void }) {
    return () =>
      h('input', {
        type: 'checkbox',
        checked: props.checked,
        'data-testid': 'checkbox-stub',
        onChange: (e: Event) => emit('update:checked', (e.target as HTMLInputElement).checked),
      })
  },
}

const AccordionStub = {
  props: ['type', 'defaultValue'],
  setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => h('div', { 'data-testid': 'accordion' }, slots.default?.())
  },
}

const AccordionItemStub = {
  props: ['value'],
  setup(
    props: { value: string },
    { slots }: { slots: Record<string, (...args: unknown[]) => unknown> },
  ) {
    return () => h('div', { 'data-testid': `accordion-item-${props.value}` }, slots.default?.())
  },
}

const AccordionTriggerStub = {
  setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => h('div', { 'data-testid': 'accordion-trigger' }, slots.default?.())
  },
}

const AccordionContentStub = {
  setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => h('div', { 'data-testid': 'accordion-content' }, slots.default?.())
  },
}

const CardStub = {
  setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => h('div', { 'data-testid': 'card' }, slots.default?.())
  },
}

const CardTitleStub = {
  setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => h('div', { 'data-testid': 'card-title' }, slots.default?.())
  },
}

const CardContentStub = {
  setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => h('div', { 'data-testid': 'card-content' }, slots.default?.())
  },
}

const IconFileDescriptionStub = {
  setup() {
    return () => h('span', { 'data-testid': 'icon-file' }, '📄')
  },
}

const IconCheckStub = {
  setup() {
    return () => h('span', { 'data-testid': 'icon-check' }, '✓')
  },
}

const LabelStub = {
  setup(_props: unknown, { slots }: { slots: Record<string, (...args: unknown[]) => unknown> }) {
    return () => h('label', {}, slots.default?.())
  },
}

const InputStub = {
  props: ['modelValue', 'name'],
  emits: ['update:modelValue'],
  setup(
    props: { modelValue?: string; name?: string },
    { emit }: { emit: (event: string, value: string) => void },
  ) {
    return () =>
      h('input', {
        'data-testid': 'input-stub',
        value: props.modelValue || '',
        name: props.name,
        onInput: (e: Event) => emit('update:modelValue', (e.target as HTMLInputElement).value),
      })
  },
}

const TextareaStub = {
  props: ['modelValue', 'name'],
  emits: ['update:modelValue'],
  setup(
    props: { modelValue?: string; name?: string },
    { emit }: { emit: (event: string, value: string) => void },
  ) {
    return () =>
      h('textarea', {
        'data-testid': 'textarea-stub',
        value: props.modelValue || '',
        name: props.name,
        onInput: (e: Event) => emit('update:modelValue', (e.target as HTMLTextAreaElement).value),
      })
  },
}

const ButtonStub = {
  props: ['type', 'variant', 'disabled'],
  setup(
    props: { type?: string; variant?: string; disabled?: boolean },
    {
      slots,
      attrs,
    }: { slots: Record<string, (...args: unknown[]) => unknown>; attrs: Record<string, unknown> },
  ) {
    return () =>
      h(
        'button',
        {
          type: props.type || 'button',
          'data-testid': 'button-stub',
          disabled: props.disabled,
          ...attrs,
        },
        slots.default?.(),
      )
  },
}

function createMockProblem(
  overrides: Partial<ProblemDescriptionFormData> = {},
): ProblemDescriptionFormData {
  return {
    title: 'Two Sum',
    slug: 'two-sum',
    difficulty: Difficulty.EASY,
    status: ProblemStatus.SOLVED,
    isPremium: false,
    isPublished: true,
    summary: 'Find two numbers that add up to a target.',
    content:
      'Given an array of integers `nums` and an integer `target`, return indices of the two numbers such that they add up to `target`.',
    examples: [
      {
        input: '[2,7,11,15], target = 9',
        output: '[0,1]',
        explanation: 'Because nums[0] + nums[1] == 9',
      },
    ],
    constraints: ['2 <= nums.length <= 10^4'],
    hints: ['Use a hash map'],
    tags: ['array', 'hash-table'],
    ...overrides,
  }
}

function mountDescriptionForm(props = {}) {
  return mount(DescriptionForm, {
    props: {
      isEdit: false,
      ...props,
    },
    global: {
      stubs: {
        MarkdownEditor: MarkdownEditorStub,
        LivePreviewPanel: LivePreviewPanelStub,
        TagsSelector: TagsSelectorStub,
        ExamplesEditor: ExamplesEditorStub,
        ConstraintsEditor: ConstraintsEditorStub,
        HintsEditor: HintsEditorStub,
        Select: SelectStub,
        SelectTrigger: SelectTriggerStub,
        SelectValue: SelectValueStub,
        SelectContent: SelectContentStub,
        SelectItem: SelectItemStub,
        Checkbox: CheckboxStub,
        Accordion: AccordionStub,
        AccordionItem: AccordionItemStub,
        AccordionTrigger: AccordionTriggerStub,
        AccordionContent: AccordionContentStub,
        Card: CardStub,
        CardTitle: CardTitleStub,
        CardContent: CardContentStub,
        IconFileDescription: IconFileDescriptionStub,
        IconCheck: IconCheckStub,
        Label: LabelStub,
        Button: ButtonStub,
        Input: InputStub,
        Textarea: TextareaStub,
      },
    },
    attachTo: document.body,
  })
}

describe('DescriptionForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  describe('form initialization', () => {
    it('renders all form sections', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      expect(wrapper.find('[data-testid="accordion-item-basic"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="accordion-item-description"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="accordion-item-examples"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="accordion-item-constraints"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="accordion-item-hints"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="accordion-item-tags"]').exists()).toBe(true)
    })

    it('initializes with default values when no problem prop is provided', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const titleInput = wrapper.find('input[name="title"]')
      const slugInput = wrapper.find('input[name="slug"]')

      expect((titleInput.element as HTMLInputElement).value).toBe('')
      expect((slugInput.element as HTMLInputElement).value).toBe('')
    })

    it('syncs problem prop into form fields on mount', async () => {
      const problem = createMockProblem()
      const wrapper = mountDescriptionForm({ problem })
      await flushPromises()

      const titleInput = wrapper.find('input[name="title"]')
      const slugInput = wrapper.find('input[name="slug"]')
      const summaryTextarea = wrapper.find('textarea[name="summary"]')

      expect((titleInput.element as HTMLInputElement).value).toBe(problem.title)
      expect((slugInput.element as HTMLInputElement).value).toBe(problem.slug)
      expect((summaryTextarea.element as HTMLTextAreaElement).value).toBe(problem.summary)
    })

    it('updates form when problem prop changes', async () => {
      const wrapper = mountDescriptionForm({ problem: createMockProblem() })
      await flushPromises()

      const newProblem = createMockProblem({ title: 'Three Sum', slug: 'three-sum' })
      await wrapper.setProps({ problem: newProblem })
      await flushPromises()
      await nextTick()

      const titleInput = wrapper.find('input[name="title"]')
      expect((titleInput.element as HTMLInputElement).value).toBe('Three Sum')
    })

    it('resets form when problem prop is removed', async () => {
      const wrapper = mountDescriptionForm({ problem: createMockProblem() })
      await flushPromises()

      await wrapper.setProps({ problem: undefined })
      await flushPromises()
      await nextTick()

      const titleInput = wrapper.find('input[name="title"]')
      expect((titleInput.element as HTMLInputElement).value).toBe('')
    })
  })

  describe('field editing', () => {
    it('updates title field on input', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const titleInput = wrapper.find('input[name="title"]')
      await titleInput.setValue('New Problem Title')
      await flushPromises()

      expect((titleInput.element as HTMLInputElement).value).toBe('New Problem Title')
    })

    it('updates slug field on input', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const slugInput = wrapper.find('input[name="slug"]')
      await slugInput.setValue('new-problem-slug')
      await flushPromises()

      expect((slugInput.element as HTMLInputElement).value).toBe('new-problem-slug')
    })

    it('updates summary field on input', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const summaryTextarea = wrapper.find('textarea[name="summary"]')
      await summaryTextarea.setValue('New summary text')
      await flushPromises()

      expect((summaryTextarea.element as HTMLTextAreaElement).value).toBe('New summary text')
    })

    it('updates markdown editor content', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const markdownEditor = wrapper.find('[data-testid="markdown-editor"]')
      await markdownEditor.setValue('New markdown content')
      await flushPromises()

      expect((markdownEditor.element as HTMLTextAreaElement).value).toBe('New markdown content')
    })

    it('updates difficulty select', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const selects = wrapper.findAll('[data-testid="select-stub"]')
      const difficultySelect = selects[0]
      await difficultySelect.setValue(Difficulty.HARD)
      await flushPromises()

      expect((difficultySelect.element as HTMLSelectElement).value).toBe(Difficulty.HARD)
    })

    it('updates status select', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const selects = wrapper.findAll('[data-testid="select-stub"]')
      const statusSelect = selects[1]
      await statusSelect.setValue(ProblemStatus.ATTEMPTED)
      await flushPromises()

      expect((statusSelect.element as HTMLSelectElement).value).toBe(ProblemStatus.ATTEMPTED)
    })

    it('updates premium checkbox', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const checkboxes = wrapper.findAll('[data-testid="checkbox-stub"]')
      const premiumCheckbox = checkboxes[0]
      await premiumCheckbox.setValue(true)
      await flushPromises()

      expect((premiumCheckbox.element as HTMLInputElement).checked).toBe(true)
    })

    it('updates published checkbox', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const checkboxes = wrapper.findAll('[data-testid="checkbox-stub"]')
      const publishedCheckbox = checkboxes[1]
      await publishedCheckbox.setValue(true)
      await flushPromises()

      expect((publishedCheckbox.element as HTMLInputElement).checked).toBe(true)
    })
  })

  describe('validation', () => {
    it('displays validation errors for required fields on submit', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const formComponent = wrapper.findComponent(DescriptionForm)
      const result = await formComponent.vm.form.validate()
      await flushPromises()
      await nextTick()

      expect(result.valid).toBe(false)
      expect(formComponent.vm.form.errors.value.title).toBe('Required')
      expect(formComponent.vm.form.errors.value.slug).toBe('Required')
      expect(formComponent.vm.form.errors.value.content).toBe('Required')
      expect(formComponent.vm.form.errors.value.examples).toBe('Required')
      expect(formComponent.vm.form.errors.value.constraints).toBe('Required')
    })

    it('displays slug format validation error', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const slugInput = wrapper.find('input[name="slug"]')
      await slugInput.setValue('Invalid Slug With Spaces')
      await flushPromises()

      const formComponent = wrapper.findComponent(DescriptionForm)
      await formComponent.vm.form.validate()
      await flushPromises()
      await nextTick()

      expect(wrapper.text()).toContain(
        'Slug must contain only lowercase letters, numbers, and hyphens',
      )
    })

    it('does not emit submit when validation fails', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      await wrapper.find('form').trigger('submit')
      await flushPromises()

      expect(wrapper.emitted('submit')).toBeFalsy()
    })
  })

  describe('submit', () => {
    it('emits submit with correct data when form is valid', async () => {
      const problem = createMockProblem()
      const wrapper = mountDescriptionForm({ problem })
      await flushPromises()

      await wrapper.find('form').trigger('submit')
      await flushPromises()
      await new Promise((resolve) => setTimeout(resolve, 50))

      expect(wrapper.emitted('submit')).toBeTruthy()
      expect(wrapper.emitted('submit')![0]).toEqual([
        expect.objectContaining({
          title: problem.title,
          slug: problem.slug,
          difficulty: problem.difficulty,
          status: problem.status,
          isPremium: problem.isPremium,
          isPublished: problem.isPublished,
          summary: problem.summary,
          content: problem.content,
          examples: problem.examples,
          constraints: problem.constraints,
          hints: problem.hints,
          tags: problem.tags,
        }),
      ])
    })

    it('emits submit with updated field values', async () => {
      const problem = createMockProblem()
      const wrapper = mountDescriptionForm({ problem })
      await flushPromises()

      const titleInput = wrapper.find('input[name="title"]')
      await titleInput.setValue('Updated Title')
      await flushPromises()

      const markdownEditor = wrapper.find('[data-testid="markdown-editor"]')
      await markdownEditor.setValue('Updated content')
      await flushPromises()

      await wrapper.find('form').trigger('submit')
      await flushPromises()
      await new Promise((resolve) => setTimeout(resolve, 50))

      const emittedData = wrapper.emitted('submit')![0][0] as ProblemDescriptionFormData
      expect(emittedData.title).toBe('Updated Title')
      expect(emittedData.content).toBe('Updated content')
      expect(emittedData.slug).toBe(problem.slug)
      expect(emittedData.examples).toEqual(problem.examples)
    })

    it('shows save button text for create mode', async () => {
      const wrapper = mountDescriptionForm({ isEdit: false })
      await flushPromises()

      const submitButton = wrapper.find('button[type="submit"]')
      expect(submitButton.text()).toContain('problems.descriptionForm.saveDescription')
    })

    it('shows update button text for edit mode', async () => {
      const wrapper = mountDescriptionForm({ isEdit: true })
      await flushPromises()

      const submitButton = wrapper.find('button[type="submit"]')
      expect(submitButton.text()).toContain('problems.descriptionForm.updateDescription')
    })
  })

  describe('cancel', () => {
    it('emits cancel event when cancel button is clicked', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const formComponent = wrapper.findComponent(DescriptionForm)
      await formComponent.vm.cancel()

      expect(wrapper.emitted('cancel')).toBeTruthy()
    })
  })

  describe('loading state', () => {
    it('disables submit button when loading is true', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const formComponent = wrapper.findComponent(DescriptionForm)
      formComponent.vm.setLoading(true)
      await nextTick()

      const submitButton = wrapper.find('button[type="submit"]')
      expect((submitButton.element as HTMLButtonElement).disabled).toBe(true)
    })

    it('enables submit button when loading is false', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const formComponent = wrapper.findComponent(DescriptionForm)
      formComponent.vm.setLoading(true)
      await nextTick()

      formComponent.vm.setLoading(false)
      await nextTick()

      const submitButton = wrapper.find('button[type="submit"]')
      expect((submitButton.element as HTMLButtonElement).disabled).toBe(false)
    })

    it('shows saving text when loading', async () => {
      const wrapper = mountDescriptionForm()
      await flushPromises()

      const formComponent = wrapper.findComponent(DescriptionForm)
      formComponent.vm.setLoading(true)
      await nextTick()

      expect(wrapper.text()).toContain('problems.descriptionForm.saving')
    })
  })

  describe('form serialization', () => {
    it('emits all form fields in correct shape', async () => {
      const problem = createMockProblem({
        title: 'Test Problem',
        slug: 'test-problem',
        difficulty: Difficulty.MEDIUM,
        status: ProblemStatus.TODO,
        isPremium: true,
        isPublished: false,
        summary: 'A test problem summary',
        content: 'Test problem content',
        examples: [{ input: '1', output: '2', explanation: 'Because 1+1=2' }],
        constraints: ['n <= 100'],
        hints: ['Think about edge cases'],
        tags: ['math'],
      })

      const wrapper = mountDescriptionForm({ problem })
      await flushPromises()

      await wrapper.find('form').trigger('submit')
      await flushPromises()
      await new Promise((resolve) => setTimeout(resolve, 50))

      const emitted = wrapper.emitted('submit')![0][0] as ProblemDescriptionFormData

      expect(emitted).toMatchObject({
        title: 'Test Problem',
        slug: 'test-problem',
        difficulty: Difficulty.MEDIUM,
        status: ProblemStatus.TODO,
        isPremium: true,
        isPublished: false,
        summary: 'A test problem summary',
        content: 'Test problem content',
        examples: [{ input: '1', output: '2', explanation: 'Because 1+1=2' }],
        constraints: ['n <= 100'],
        hints: ['Think about edge cases'],
        tags: ['math'],
      })
    })

    it('handles optional summary as empty string', async () => {
      const problem = createMockProblem({ summary: '' })
      const wrapper = mountDescriptionForm({ problem })
      await flushPromises()

      await wrapper.find('form').trigger('submit')
      await flushPromises()
      await new Promise((resolve) => setTimeout(resolve, 50))

      const emitted = wrapper.emitted('submit')![0][0] as ProblemDescriptionFormData
      expect(emitted.summary).toBe('')
    })

    it('handles empty hints array', async () => {
      const problem = createMockProblem({ hints: [] })
      const wrapper = mountDescriptionForm({ problem })
      await flushPromises()

      await wrapper.find('form').trigger('submit')
      await flushPromises()
      await new Promise((resolve) => setTimeout(resolve, 50))

      const emitted = wrapper.emitted('submit')![0][0] as ProblemDescriptionFormData
      expect(emitted.hints).toEqual([])
    })

    it('handles empty tags array', async () => {
      const problem = createMockProblem({ tags: [] })
      const wrapper = mountDescriptionForm({ problem })
      await flushPromises()

      await wrapper.find('form').trigger('submit')
      await flushPromises()
      await new Promise((resolve) => setTimeout(resolve, 50))

      const emitted = wrapper.emitted('submit')![0][0] as ProblemDescriptionFormData
      expect(emitted.tags).toEqual([])
    })
  })
})
