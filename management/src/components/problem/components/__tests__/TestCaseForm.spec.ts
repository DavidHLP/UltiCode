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
  inputText: 'foo',
  outputText: 'bar',
  isSample: true,
  isHidden: false,
  explanation: '',
}

/**
 * Stub RadioGroup/RadioGroupItem as plain inputs so the test can drive them
 * via the v-model. The full reka-ui component is exercised in E2E; this unit
 * test focuses on the TestCaseForm's two-way mapping between the radio's
 * CaseScope value and the underlying (isSample, isHidden) flag pair.
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
  it('reflects SAMPLE initial state when isSample=true and isHidden=false', () => {
    const wrapper = mountForm({ ...baseFormData, isSample: true, isHidden: false })
    const sample = wrapper.find('[data-testid="scope-sample"]')
    const hidden = wrapper.find('[data-testid="scope-hidden"]')
    expect((sample.element as HTMLInputElement).checked).toBe(true)
    expect((hidden.element as HTMLInputElement).checked).toBe(false)
  })

  it('reflects HIDDEN initial state when isSample=false and isHidden=true', () => {
    const wrapper = mountForm({ ...baseFormData, isSample: false, isHidden: true })
    const sample = wrapper.find('[data-testid="scope-sample"]')
    const hidden = wrapper.find('[data-testid="scope-hidden"]')
    expect((sample.element as HTMLInputElement).checked).toBe(false)
    expect((hidden.element as HTMLInputElement).checked).toBe(true)
  })

  it('emits formData with (isSample=false, isHidden=true) when user picks HIDDEN', async () => {
    const wrapper = mountForm({ ...baseFormData, isSample: true, isHidden: false })
    const hidden = wrapper.find('[data-testid="scope-hidden"]')
    await hidden.setValue()
    await nextTick()
    const emitted = wrapper.emitted('update:formData')
    expect(emitted).toBeTruthy()
    const lastEmit = emitted!.at(-1)![0] as CreateTestCaseDto
    expect(lastEmit.isSample).toBe(false)
    expect(lastEmit.isHidden).toBe(true)
  })

  it('emits formData with (isSample=true, isHidden=false) when user picks SAMPLE', async () => {
    const wrapper = mountForm({ ...baseFormData, isSample: false, isHidden: true })
    const sample = wrapper.find('[data-testid="scope-sample"]')
    await sample.setValue()
    await nextTick()
    const emitted = wrapper.emitted('update:formData')
    expect(emitted).toBeTruthy()
    const lastEmit = emitted!.at(-1)![0] as CreateTestCaseDto
    expect(lastEmit.isSample).toBe(true)
    expect(lastEmit.isHidden).toBe(false)
  })

  it('always emits both flags explicitly — never a half-defined state', async () => {
    const wrapper = mountForm({ ...baseFormData, isSample: true, isHidden: false })
    const hidden = wrapper.find('[data-testid="scope-hidden"]')
    await hidden.setValue()
    await nextTick()
    const lastEmit = wrapper.emitted('update:formData')!.at(-1)![0] as CreateTestCaseDto
    expect(typeof lastEmit.isSample).toBe('boolean')
    expect(typeof lastEmit.isHidden).toBe('boolean')
    const xorSatisfied = lastEmit.isSample !== lastEmit.isHidden
    expect(xorSatisfied).toBe(true)
  })
})
