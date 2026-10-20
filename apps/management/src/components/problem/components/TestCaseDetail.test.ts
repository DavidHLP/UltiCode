import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import TestCaseDetail from './TestCaseDetail.vue'
import type { TestCase } from '@/api/admin/test-cases'

describe('Test case scope switch', () => {
  it.each([true, false])('renders sample=%s and requests the opposite scope', async (isSample) => {
    const testCase: TestCase = {
      id: 'test-case',
      problemId: 1,
      isSample,
      isHidden: !isSample,
      testOrder: 1,
      inputText: '1',
      outputText: '2',
      createdAt: '',
      updatedAt: '',
    }
    const wrapper = mount(TestCaseDetail, {
      props: { testCase },
      global: {
        plugins: [
          createI18n({
            legacy: false,
            locale: 'en',
            messages: { en: {} },
            missingWarn: false,
            fallbackWarn: false,
          }),
        ],
      },
    })
    const control = wrapper.get('[role="switch"]')
    expect(control.attributes('aria-checked')).toBe(String(isSample))
    await control.trigger('click')
    expect(wrapper.emitted('set-scope')).toEqual([[testCase, isSample ? 'HIDDEN' : 'SAMPLE']])
    expect(testCase.isSample).toBe(isSample)
    wrapper.unmount()
  })
})
