import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import TestCasesEditor from '../TestCasesEditor.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, values?: { number?: number }) => `${key}${values?.number ?? ''}`,
  }),
}))

const cases = [
  { id: 'first', input: '1', output: '1' },
  { id: 'second', input: '2', output: '2' },
]

describe('TestCasesEditor accessible controls', () => {
  it('uses sibling selection/removal buttons and emits remaining cases', async () => {
    const wrapper = mount(TestCasesEditor, { props: { modelValue: cases } })
    expect(wrapper.find('button button').exists()).toBe(false)
    const remove = wrapper.get('button[aria-label="common.remove"]')
    await remove.trigger('click')
    const updates = wrapper.emitted('update:modelValue')!
    expect(updates.at(-1)?.[0]).toEqual([cases[1]])
    expect(wrapper.get('textarea').element.value).toBe('2')
    wrapper.unmount()
  })

  it('links editable fields to their labels', () => {
    const wrapper = mount(TestCasesEditor, { props: { modelValue: cases } })
    for (const label of wrapper.findAll('label')) {
      expect(label.attributes('for')).toBeTruthy()
      expect(wrapper.find(`[id="${label.attributes('for')}"]`).exists()).toBe(true)
    }
    wrapper.unmount()
  })
})
