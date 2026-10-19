import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ProblemForm from '../ProblemForm.vue'

vi.mock('vue-i18n', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-i18n')>()),
  useI18n: () => ({ t: (key: string) => key }),
}))
vi.mock('@/components/problem/MarkdownEditor.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/problem/TestCasesEditor.vue', () => ({ default: { template: '<div />' } }))

describe('ProblemForm official controls', () => {
  it('toggles each checkbox once and preserves panel click behavior', async () => {
    const wrapper = mount(ProblemForm)
    for (const name of ['premium', 'published']) {
      const checkbox = wrapper.get(`[role="checkbox"][aria-label="problems.form.${name}"]`)
      expect(checkbox.attributes('aria-checked')).toBe('false')
      await checkbox.trigger('click')
      expect(checkbox.attributes('aria-checked')).toBe('true')
      await checkbox.trigger('click')
      expect(checkbox.attributes('aria-checked')).toBe('false')
      await checkbox.element.parentElement!.click()
      await wrapper.vm.$nextTick()
      expect(checkbox.attributes('aria-checked')).toBe('true')
    }
    wrapper.unmount()
  })

  it('links title, slug and summary labels to their controls', () => {
    const wrapper = mount(ProblemForm)
    for (const field of ['title', 'slug', 'summary']) {
      const label = wrapper
        .findAll('label')
        .find((node) => node.text() === `problems.form.${field}`)!
      expect(label.attributes('for')).toBeTruthy()
      expect(wrapper.get(`[id="${label.attributes('for')}"]`).element.tagName).toBe(
        field === 'summary' ? 'TEXTAREA' : 'INPUT',
      )
    }
    wrapper.unmount()
  })
})
