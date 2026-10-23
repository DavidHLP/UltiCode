import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ScoringRuleForm from './ScoringRuleForm.vue'
import type { ScoringRule } from '@/api/admin/scoring-rules'

const rule: ScoringRule = {
  id: 'test-rule',
  name: 'Test rule',
  description: '',
  baseScorePerProblem: 100,
  timeBonusPerMinute: 1,
  wrongAnswerPenalty: 5,
  timeLimitPenalty: 0,
  firstSolveBonus: 10,
  fullScoreBonus: 0,
  isDefault: true,
  isActive: true,
  createdAt: '',
  updatedAt: '',
}
let wrapper: VueWrapper | undefined
const dialogShell = { template: '<div><slot /></div>' }

async function mountForm(overrides: Partial<ScoringRule> = {}, unmountOnClose = false) {
  wrapper = mount(ScoringRuleForm, {
    props: { open: true, ruleToEdit: { ...rule, ...overrides } },
    attachTo: document.body,
    global: {
      stubs: {
        Dialog: unmountOnClose
          ? { props: ['open'], template: '<div v-if="open"><slot /></div>' }
          : dialogShell,
        DialogContent: dialogShell,
        DialogTitle: dialogShell,
        DialogDescription: dialogShell,
      },
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
  await flushPromises()
  return wrapper
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('Scoring rule form controls', () => {
  it('restores new-rule defaults after closing and reopening the same form', async () => {
    const form = await mountForm({}, true)
    await form.setProps({ ruleToEdit: null })
    await flushPromises()
    const numbers = () =>
      form.findAll('input[type="number"]').map((input) => (input.element as HTMLInputElement).value)
    expect(numbers()).toEqual(['100', '1', '5', '0', '10', '0'])
    await form.setProps({ open: false })
    await flushPromises()
    await form.setProps({ open: true })
    await flushPromises()
    expect(numbers()).toEqual(['100', '1', '5', '0', '10', '0'])
    expect(form.get('[role="switch"]').attributes('aria-checked')).toBe('false')
  })

  it('reserves space above focused fields in the scrollable dialog', async () => {
    const form = await mountForm()
    expect(form.find('.scroll-pt-8').exists()).toBe(true)
  })

  it('shows an existing default rule in the real switch', async () => {
    const form = await mountForm()
    expect(form.get('[role="switch"]').attributes('aria-checked')).toBe('true')
  })

  it('validates a native submit and emits the selected default value once', async () => {
    const form = await mountForm({ isDefault: false })
    await form.get('[role="switch"]').trigger('click')
    const event = new Event('submit', { cancelable: true, bubbles: true })
    form.get('form').element.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
    await vi.waitFor(() => expect(form.emitted('save')).toHaveLength(1))
    expect(form.emitted('save')?.[0][0]).toMatchObject({
      name: rule.name,
      isDefault: true,
      baseScorePerProblem: 100,
    })
  })

  it('shows validation errors and does not emit invalid data', async () => {
    const form = await mountForm({ name: '' })
    await form.get('form').trigger('submit')
    await vi.waitFor(() => expect(form.text()).toContain('scoringRules.form.nameRequired'))
    expect(form.emitted('save')).toBeUndefined()
  })
})
