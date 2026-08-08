import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createApp, h, nextTick } from 'vue'
import LivePreviewPanel from './LivePreviewPanel.vue'
import type { ProblemDescriptionFormData } from '@/lib/schemas/problemDescription'
import { Difficulty, ProblemStatus } from '@/api/admin/problems'

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
  toast: { success: vi.fn() },
}))

vi.mock('@/components/ui/separator', () => ({
  Separator: {
    setup(_props: unknown, { slots }: { slots: Record<string, () => unknown> }) {
      return () =>
        h('hr', { 'data-slot': 'separator' }, slots.default?.() as Parameters<typeof h>[2])
    },
  },
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

function createMockData(
  overrides: Partial<ProblemDescriptionFormData> = {},
): ProblemDescriptionFormData {
  return {
    title: 'Two Sum',
    slug: 'two-sum',
    difficulty: Difficulty.EASY,
    status: ProblemStatus.SOLVED,
    isPremium: false,
    isPublished: true,
    content:
      'Given an array of integers, return indices of the two numbers such that they add up to a target.',
    examples: [
      {
        input: '[2,7,11,15], target = 9',
        output: '[0,1]',
        explanation: 'Because nums[0] + nums[1] == 9',
      },
    ],
    constraints: ['2 \u003c= nums.length \u003c= 10^4', '-10^9 \u003c= nums[i] \u003c= 10^9'],
    hints: ['Try a hash map', 'Consider one-pass approach'],
    tags: ['array', 'hash-table'],
    languages: [],
    ...overrides,
  }
}

async function mountComponent(data: ProblemDescriptionFormData) {
  const container = document.createElement('div')
  document.body.appendChild(container)

  const app = createApp({
    setup() {
      return () => h(LivePreviewPanel, { data })
    },
  })

  app.mount(container)
  await nextTick()
  await nextTick()

  return { container, app }
}

describe('LivePreviewPanel', () => {
  beforeEach(() => {
    cleanup()
  })

  it('renders title and slug', async () => {
    const { container } = await mountComponent(createMockData())

    expect(container.textContent).toContain('Two Sum')
    expect(container.textContent).toContain('two-sum')
  })

  it('renders untitled fallback when title is empty', async () => {
    const { container } = await mountComponent(createMockData({ title: '' }))

    expect(container.textContent).toContain('problems.preview.untitled')
  })

  it('renders difficulty badge', async () => {
    const { container } = await mountComponent(createMockData({ difficulty: Difficulty.HARD }))

    const badge = container.querySelector('.terminal-badge')
    expect(badge).toBeTruthy()
    expect(badge!.textContent).toContain('problems.difficulty.HARD')
  })

  it('renders premium badge when isPremium is true', async () => {
    const { container } = await mountComponent(createMockData({ isPremium: true }))

    const badges = container.querySelectorAll('[data-slot="badge"]')
    const premiumBadge = Array.from(badges).find((badge) =>
      badge.textContent?.includes('problems.badges.premium'),
    )
    expect(premiumBadge).toBeTruthy()
  })

  it('does not render premium badge when isPremium is false', async () => {
    const { container } = await mountComponent(createMockData({ isPremium: false }))

    const badges = container.querySelectorAll('[data-slot="badge"]')
    const premiumBadge = Array.from(badges).find((badge) =>
      badge.textContent?.includes('problems.badges.premium'),
    )
    expect(premiumBadge).toBeFalsy()
  })

  it('renders published badge when isPublished is true', async () => {
    const { container } = await mountComponent(createMockData({ isPublished: true }))

    expect(container.textContent).toContain('problems.published.published')
  })

  it('renders draft badge when isPublished is false', async () => {
    const { container } = await mountComponent(createMockData({ isPublished: false }))

    expect(container.textContent).toContain('problems.published.draft')
  })

  it('renders summary when provided', async () => {
    const { container } = await mountComponent(
      createMockData({ summary: 'A classic array problem.' }),
    )

    const summaryText = Array.from(container.querySelectorAll('p')).find((paragraph) =>
      paragraph.textContent?.includes('A classic array problem.'),
    )
    expect(summaryText).toBeTruthy()
  })

  it('does not render summary section when summary is empty', async () => {
    const { container } = await mountComponent(createMockData({ summary: undefined }))

    const summaryElements = container.querySelectorAll('.text-muted-foreground')
    const hasSummary = Array.from(summaryElements).some((element) =>
      element.textContent?.includes('A classic array problem.'),
    )
    expect(hasSummary).toBe(false)
  })

  it('renders markdown content via DescriptionMarkdown', async () => {
    const { container } = await mountComponent(createMockData())

    const markdownContainer = container.querySelector('.description-markdown')
    expect(markdownContainer).toBeTruthy()
  })

  it('renders examples in markdown content', async () => {
    const { container } = await mountComponent(createMockData())

    expect(container.textContent).toContain('problems.descriptionDisplay.example')
    expect(container.textContent).toContain('[2,7,11,15], target = 9')
    expect(container.textContent).toContain('[0,1]')
  })

  it('renders constraints in markdown content', async () => {
    const { container } = await mountComponent(createMockData())

    expect(container.textContent).toContain('2 <= nums.length <= 10^4')
    expect(container.textContent).toContain('-10^9 <= nums[i] <= 10^9')
  })

  it('renders collapsible hints section when hints exist', async () => {
    const { container } = await mountComponent(createMockData())

    const collapsible = container.querySelector('[data-slot="collapsible"]')
    expect(collapsible).toBeTruthy()

    expect(container.textContent).toContain('problems.display.hints')
    expect(container.textContent).toContain('Try a hash map')
    expect(container.textContent).toContain('Consider one-pass approach')
  })

  it('does not render hints section when no hints exist', async () => {
    const { container } = await mountComponent(createMockData({ hints: [] }))

    const collapsible = container.querySelector('[data-slot="collapsible"]')
    expect(collapsible).toBeFalsy()
  })

  it('displays correct hint count badge', async () => {
    const { container } = await mountComponent(
      createMockData({ hints: ['Hint 1', 'Hint 2', 'Hint 3'] }),
    )

    expect(container.textContent).toContain('3')
  })

  it('renders numbered hint items', async () => {
    const { container } = await mountComponent(createMockData({ hints: ['First', 'Second'] }))

    const collapsibleContent = container.querySelector('[data-slot="collapsible-content"]')
    const listItems = collapsibleContent?.querySelectorAll('li') || []
    expect(listItems.length).toBe(2)
    expect(listItems[0]!.textContent).toContain('1')
    expect(listItems[0]!.textContent).toContain('First')
    expect(listItems[1]!.textContent).toContain('2')
    expect(listItems[1]!.textContent).toContain('Second')
  })

  it('updates rendered content when data changes', async () => {
    const container = document.createElement('div')
    document.body.appendChild(container)

    const app = createApp({
      setup() {
        const data = createMockData({ title: 'Original Title' })
        return () => h(LivePreviewPanel, { data })
      },
    })

    app.mount(container)
    await nextTick()

    expect(container.textContent).toContain('Original Title')

    app.unmount()
    const newApp = createApp({
      setup() {
        const data = createMockData({ title: 'Updated Title' })
        return () => h(LivePreviewPanel, { data })
      },
    })
    newApp.mount(container)
    await nextTick()

    expect(container.textContent).toContain('Updated Title')
  })

  it('renders with medium difficulty badge', async () => {
    const { container } = await mountComponent(createMockData({ difficulty: Difficulty.MEDIUM }))

    const badge = container.querySelector('.terminal-badge')
    expect(badge!.textContent).toContain('problems.difficulty.MEDIUM')
  })

  it('renders em dash when slug is empty', async () => {
    const { container } = await mountComponent(createMockData({ slug: '' }))

    expect(container.textContent).toContain('—')
  })
})
