import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createApp, h, nextTick } from 'vue'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { z } from 'zod'
import ExamplesEditor from './ExamplesEditor.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: { value: 'en-US' },
  }),
}))

vi.mock('@/components/ui/collapsible', () => ({
  Collapsible: {
    props: ['defaultOpen'],
    setup(_props: unknown, { slots }: { slots: Record<string, () => unknown> }) {
      return () =>
        h('div', { 'data-slot': 'collapsible' }, slots.default?.() as Parameters<typeof h>[2])
    },
  },
  CollapsibleContent: {
    setup(_props: unknown, { slots }: { slots: Record<string, () => unknown> }) {
      return () =>
        h(
          'div',
          { 'data-slot': 'collapsible-content' },
          slots.default?.() as Parameters<typeof h>[2],
        )
    },
  },
  CollapsibleTrigger: {
    setup(_props: unknown, { slots }: { slots: Record<string, () => unknown> }) {
      return () =>
        h(
          'button',
          { 'data-slot': 'collapsible-trigger' },
          slots.default?.() as Parameters<typeof h>[2],
        )
    },
  },
}))

function cleanup() {
  document.body.innerHTML = ''
}

async function mountWithForm(
  initialValues: { examples: Array<{ input: string; output: string; explanation?: string }> } = {
    examples: [],
  },
) {
  const container = document.createElement('div')
  document.body.appendChild(container)

  const app = createApp({
    setup() {
      useForm({
        validationSchema: toTypedSchema(
          z.object({
            examples: z.array(
              z.object({
                input: z.string(),
                output: z.string(),
                explanation: z.string().optional(),
              }),
            ),
          }),
        ),
        initialValues,
      })

      return () => h(ExamplesEditor)
    },
  })

  app.mount(container)
  await nextTick()
  await nextTick()

  return { container, app }
}

describe('ExamplesEditor', () => {
  beforeEach(() => {
    cleanup()
  })

  it('renders empty state when no examples exist', async () => {
    const { container } = await mountWithForm()

    const emptyText = container.querySelector('.text-muted-foreground')
    expect(emptyText).toBeTruthy()
    expect(emptyText!.textContent).toContain('problems.casesDisplay.noCasesDescription')

    const addButton = container.querySelector('button')
    expect(addButton).toBeTruthy()
    expect(addButton!.textContent).toContain('problems.form.examples')
  })

  it('renders textareas with correct initial values', async () => {
    const { container } = await mountWithForm({
      examples: [{ input: 'hello', output: 'world', explanation: 'test explanation' }],
    })

    const textareas = container.querySelectorAll('textarea')
    expect(textareas.length).toBe(3)
    expect((textareas[0] as HTMLTextAreaElement).value).toBe('hello')
    expect((textareas[1] as HTMLTextAreaElement).value).toBe('world')
    expect((textareas[2] as HTMLTextAreaElement).value).toBe('test explanation')
  })

  it('renders multiple example cards', async () => {
    const { container } = await mountWithForm({
      examples: [
        { input: 'a', output: 'b', explanation: '' },
        { input: 'c', output: 'd', explanation: '' },
      ],
    })

    const cards = container.querySelectorAll('[data-slot="collapsible"]')
    expect(cards.length).toBe(2)

    const labels = container.querySelectorAll('label')
    expect(labels.length).toBe(6)
  })

  it('disables delete button when at minimum items', async () => {
    const { container } = await mountWithForm({
      examples: [{ input: 'a', output: 'b', explanation: '' }],
    })

    const deleteButtons = container.querySelectorAll('button')
    const trashButton = Array.from(deleteButtons).find((btn) =>
      btn.classList.contains('text-destructive'),
    )
    expect(trashButton).toBeTruthy()
    expect(trashButton!.disabled).toBe(true)
  })

  it('disables up button on first example and down on last', async () => {
    const { container } = await mountWithForm({
      examples: [
        { input: 'first', output: '1', explanation: '' },
        { input: 'second', output: '2', explanation: '' },
      ],
    })

    const cards = container.querySelectorAll('[data-slot="collapsible"]')
    expect(cards.length).toBe(2)

    const getCardButtons = (card: Element) => {
      const header = card.querySelector('.flex-row')
      const buttons = header?.querySelectorAll('button') || []
      return {
        up: buttons[1] as HTMLButtonElement | undefined,
        down: buttons[2] as HTMLButtonElement | undefined,
      }
    }

    const card1 = getCardButtons(cards[0]!)
    const card2 = getCardButtons(cards[1]!)

    expect(card1.up!.disabled).toBe(true)
    expect(card1.down!.disabled).toBe(false)
    expect(card2.up!.disabled).toBe(false)
    expect(card2.down!.disabled).toBe(true)
  })

  it('collapsible trigger renders example label', async () => {
    const { container } = await mountWithForm({
      examples: [{ input: 'a', output: 'b', explanation: '' }],
    })

    const trigger = container.querySelector('[data-slot="collapsible-trigger"]')
    expect(trigger).toBeTruthy()
    expect(trigger!.textContent).toContain('problems.descriptionDisplay.example')
  })

  it('uses correct textarea placeholders', async () => {
    const { container } = await mountWithForm({
      examples: [{ input: '', output: '', explanation: '' }],
    })

    const textareas = container.querySelectorAll('textarea')
    expect(textareas.length).toBe(3)

    const inputPlaceholder = (textareas[0] as HTMLTextAreaElement).placeholder
    const outputPlaceholder = (textareas[1] as HTMLTextAreaElement).placeholder
    const explanationPlaceholder = (textareas[2] as HTMLTextAreaElement).placeholder

    expect(inputPlaceholder).toContain('problems.form.validation.inputRequired')
    expect(outputPlaceholder).toContain('problems.form.validation.outputRequired')
    expect(explanationPlaceholder).toContain('problems.descriptionDisplay.explanation')
  })
})
