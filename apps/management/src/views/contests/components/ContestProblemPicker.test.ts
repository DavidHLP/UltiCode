import { shallowMount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ContestProblemPicker from './ContestProblemPicker.vue'

const store = reactive({
  problems: [] as Array<{ id: string; title: string; slug: string; difficulty: string }>,
  loading: false,
  error: null as string | null,
  fetchProblems: vi.fn().mockResolvedValue(undefined),
})
vi.mock('@/stores/admin/problems', () => ({ useProblemsStore: () => store }))
beforeEach(() => {
  store.problems = []
  store.loading = false
  store.error = null
  store.fetchProblems.mockClear()
})

describe('contest problem picker short-screen layout', () => {
  it('separates failed requests from empty results, hides stale choices and retries the same search', async () => {
    vi.useFakeTimers()
    store.error = 'unavailable'
    store.problems = [{ id: '1', title: 'Stale choice', slug: 'old', difficulty: 'EASY' }]
    const wrapper = shallowMount(ContestProblemPicker, {
      props: { open: true },
      global: {
        plugins: [
          createI18n({ legacy: false, locale: 'en-US', missingWarn: false, fallbackWarn: false }),
        ],
        renderStubDefaultSlot: true,
      },
    })
    try {
      expect(wrapper.get('[role="alert"]').text()).toContain('contests.problemPicker.loadFailed')
      expect(wrapper.text()).not.toContain('Stale choice')
      await vi.advanceTimersByTimeAsync(300)
      await wrapper.findComponent({ name: 'CommandInput' }).vm.$emit('input', {
        target: { value: '两数' },
      })
      await vi.advanceTimersByTimeAsync(300)
      store.fetchProblems.mockClear()
      await wrapper.findComponent({ name: 'Button' }).vm.$emit('click')
      expect(store.fetchProblems).toHaveBeenCalledExactlyOnceWith({
        search: '两数',
        limit: 20,
        sortBy: 'title',
      })
      store.error = null
      store.loading = true
      await nextTick()
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(wrapper.get('[role="status"]').attributes('aria-label')).toBe('common.loading')
      store.loading = false
      store.problems = []
      await nextTick()
      expect(wrapper.get('[role="status"]').text()).toContain(
        'contests.problemPicker.noProblemsFound',
      )
      store.problems = [{ id: '2', title: 'Recovered choice', slug: 'new', difficulty: 'EASY' }]
      await nextTick()
      expect(wrapper.text()).toContain('Recovered choice')
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    } finally {
      wrapper.unmount()
      vi.clearAllTimers()
      vi.useRealTimers()
    }
  })

  it('keeps tablet gutters and lets the dialog scroll past the bounded results list', () => {
    vi.useFakeTimers()
    const wrapper = shallowMount(ContestProblemPicker, {
      props: { open: true },
      global: {
        plugins: [
          createI18n({ legacy: false, locale: 'en-US', missingWarn: false, fallbackWarn: false }),
        ],
        renderStubDefaultSlot: true,
      },
    })
    try {
      const dialog = wrapper.findComponent({ name: 'DialogContent' })
      expect(dialog.classes()).not.toContain('overflow-hidden')
      expect(dialog.classes()).toContain('w-[calc(100%-2rem)]')
      expect(wrapper.findComponent({ name: 'CommandList' }).classes()).toContain('max-h-[300px]')
    } finally {
      wrapper.unmount()
      vi.clearAllTimers()
      vi.useRealTimers()
    }
  })
})
