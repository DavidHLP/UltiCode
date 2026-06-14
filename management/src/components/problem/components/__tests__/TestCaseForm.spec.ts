import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { nextTick } from 'vue'
import TestCaseForm from '../TestCaseForm.vue'
import type { CreateTestCaseDto } from '@/api/admin/test-cases'

const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  messages: {
    'zh-CN': { testCases: { scope: { sample: '公开样例', hidden: '隐藏判题用例' } } },
    'en-US': { testCases: { scope: { sample: 'Public Sample', hidden: 'Hidden Judge Case' } } },
  },
})

const baseFormData: CreateTestCaseDto = {
  input_text: 'foo',
  output_text: 'bar',
  is_sample: true,
  is_hidden: false,
  explanation: '',
}

/**
 * Stub RadioGroup/RadioGroupItem as plain inputs so the test can drive them
 * via the v-model. The full reka-ui component is exercised in E2E; this unit
 * test focuses on the TestCaseForm's two-way mapping between the radio's
 * CaseScope value and the underlying (is_sample, is_hidden) flag pair.
 */
const RadioGroupStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: `
    <div>
      <input
        type="radio"
        value="SAMPLE"
        :checked="modelValue === 'SAMPLE'"
        data-testid="scope-sample"
        @change="$emit('update:modelValue', 'SAMPLE')"
      />
      <input
        type="radio"
        value="HIDDEN"
        :checked="modelValue === 'HIDDEN'"
        data-testid="scope-hidden"
        @change="$emit('update:modelValue', 'HIDDEN')"
      />
    </div>
  `,
}

function mountForm(formData: CreateTestCaseDto = { ...baseFormData }) {
  return mount(TestCaseForm, {
    props: {
      open: true,
      editingTestCase: null,
      formData,
      saving: false,
    },
    global: {
      plugins: [i18n],
      stubs: {
        Dialog: { template: '<div><slot /></div>' },
        DialogContent: { template: '<div><slot /></div>' },
        DialogHeader: { template: '<div><slot /></div>' },
        DialogTitle: { template: '<div><slot /></div>' },
        DialogFooter: { template: '<div><slot /></div>' },
        IconLoader2: true,
        RadioGroup: RadioGroupStub,
        RadioGroupItem: { template: '<div />' },
      },
    },
  })
}

describe('TestCaseForm — case scope radio', () => {
  it('reflects SAMPLE initial state when is_sample=true and is_hidden=false', () => {
    const wrapper = mountForm({ ...baseFormData, is_sample: true, is_hidden: false })
    const sample = wrapper.find('[data-testid="scope-sample"]')
    const hidden = wrapper.find('[data-testid="scope-hidden"]')
    expect((sample.element as HTMLInputElement).checked).toBe(true)
    expect((hidden.element as HTMLInputElement).checked).toBe(false)
  })

  it('reflects HIDDEN initial state when is_sample=false and is_hidden=true', () => {
    const wrapper = mountForm({ ...baseFormData, is_sample: false, is_hidden: true })
    const sample = wrapper.find('[data-testid="scope-sample"]')
    const hidden = wrapper.find('[data-testid="scope-hidden"]')
    expect((sample.element as HTMLInputElement).checked).toBe(false)
    expect((hidden.element as HTMLInputElement).checked).toBe(true)
  })

  it('emits formData with (is_sample=false, is_hidden=true) when user picks HIDDEN', async () => {
    const wrapper = mountForm({ ...baseFormData, is_sample: true, is_hidden: false })
    const hidden = wrapper.find('[data-testid="scope-hidden"]')
    await hidden.setValue()
    await nextTick()
    const emitted = wrapper.emitted('update:formData')
    expect(emitted).toBeTruthy()
    const lastEmit = emitted!.at(-1)![0] as CreateTestCaseDto
    expect(lastEmit.is_sample).toBe(false)
    expect(lastEmit.is_hidden).toBe(true)
  })

  it('emits formData with (is_sample=true, is_hidden=false) when user picks SAMPLE', async () => {
    const wrapper = mountForm({ ...baseFormData, is_sample: false, is_hidden: true })
    const sample = wrapper.find('[data-testid="scope-sample"]')
    await sample.setValue()
    await nextTick()
    const emitted = wrapper.emitted('update:formData')
    expect(emitted).toBeTruthy()
    const lastEmit = emitted!.at(-1)![0] as CreateTestCaseDto
    expect(lastEmit.is_sample).toBe(true)
    expect(lastEmit.is_hidden).toBe(false)
  })

  it('always emits both flags explicitly — never a half-defined state', async () => {
    const wrapper = mountForm({ ...baseFormData, is_sample: true, is_hidden: false })
    const hidden = wrapper.find('[data-testid="scope-hidden"]')
    await hidden.setValue()
    await nextTick()
    const lastEmit = wrapper.emitted('update:formData')!.at(-1)![0] as CreateTestCaseDto
    expect(typeof lastEmit.is_sample).toBe('boolean')
    expect(typeof lastEmit.is_hidden).toBe('boolean')
    const xorSatisfied = lastEmit.is_sample !== lastEmit.is_hidden
    expect(xorSatisfied).toBe(true)
  })
})
