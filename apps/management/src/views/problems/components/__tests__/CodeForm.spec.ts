import { mount } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import CodeForm from '../CodeForm.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

it('uses monospace for starter code without changing its whitespace or update contract', async () => {
  const starterCode = 'class Solution {\n    solve() { return 1; }\n}'
  const wrapper = mount(CodeForm, {
    props: { problem: { languages: [{ id: 'java', language: 'java', starterCode }] } },
  })
  try {
    const toggle = wrapper.findAll('button').find((button) => button.text().includes('3'))!
    await toggle.trigger('click')
    const editor = wrapper.get('textarea')
    expect(editor.classes()).toContain('font-mono')
    expect(editor.element.value).toBe(starterCode)
    const updatedCode = `${starterCode}\n// unchanged indentation`
    await editor.setValue(updatedCode)
    await wrapper.findAll('button').find((button) => button.text().includes('problems.codeForm.saveChanges'))!.trigger('click')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      languages: [{ language: 'java', starterCode: updatedCode }],
    })
  } finally {
    wrapper.unmount()
  }
})
