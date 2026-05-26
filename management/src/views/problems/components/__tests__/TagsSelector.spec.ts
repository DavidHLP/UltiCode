import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick, ref, reactive } from 'vue'
import TagsSelector from '../TagsSelector.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

const mockTags = [
  { id: 'tag-1', label: 'Array' },
  { id: 'tag-2', label: 'Hash Table' },
  { id: 'tag-3', label: 'Two Pointers' },
  { id: 'tag-4', label: 'Binary Search' },
  { id: 'tag-5', label: 'Sliding Window' },
]

function createStoreState(
  overrides: Partial<{
    allTags: typeof mockTags
    tagsLoading: boolean
    fetchAllTags: () => Promise<typeof mockTags>
  }> = {},
) {
  const allTags = ref(overrides.allTags ?? [...mockTags])
  const tagsLoading = ref(overrides.tagsLoading ?? false)
  const fetchAllTags = vi.fn().mockResolvedValue(mockTags)

  return {
    allTags,
    tagsLoading,
    fetchAllTags,
  }
}

let storeState: ReturnType<typeof createStoreState>

vi.mock('@/stores/admin/problems', () => ({
  useProblemsStore: () => reactive(storeState),
}))

describe('TagsSelector', () => {
  beforeEach(() => {
    storeState = createStoreState()
    vi.clearAllMocks()
  })

  function createWrapper(props = {}, setupStore?: (state: typeof storeState) => void) {
    if (setupStore) {
      setupStore(storeState)
    }

    return mount(TagsSelector, {
      props: {
        modelValue: [],
        ...props,
      },
    })
  }

  describe('rendering', () => {
    it('renders the component', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('[data-testid="available-tags"]').exists()).toBe(true)
    })

    it('shows selected tags when provided', () => {
      const wrapper = createWrapper({ modelValue: ['tag-1', 'tag-3'] })

      const selectedTags = wrapper.find('[data-testid="selected-tags"]')
      expect(selectedTags.exists()).toBe(true)
      expect(selectedTags.text()).toContain('Array')
      expect(selectedTags.text()).toContain('Two Pointers')
    })

    it('shows no tags selected message when empty', () => {
      const wrapper = createWrapper({ modelValue: [] })
      expect(wrapper.text()).toContain('problems.tagsSelector.noTagsSelected')
    })

    it('shows loading state', () => {
      const wrapper = createWrapper({}, (state) => {
        state.tagsLoading.value = true
      })

      expect(wrapper.text()).toContain('problems.tagsSelector.loading')
      expect(wrapper.find('[data-testid="available-tags"]').exists()).toBe(false)
    })

    it('shows all available tags', () => {
      const wrapper = createWrapper()
      const availableTags = wrapper.find('[data-testid="available-tags"]')

      mockTags.forEach((tag) => {
        expect(availableTags.text()).toContain(tag.label)
      })
    })
  })

  describe('tag selection', () => {
    it('selects a tag when clicked', async () => {
      const wrapper = createWrapper({ modelValue: [] })

      const tagButton = wrapper.findAll('[data-testid="available-tags"] button')[0]
      await tagButton.trigger('click')

      expect(wrapper.emitted('update:modelValue')).toBeTruthy()
      expect(wrapper.emitted('update:modelValue')![0]).toEqual([['tag-1']])
    })

    it('deselects a tag when clicked again', async () => {
      const wrapper = createWrapper({ modelValue: ['tag-1'] })

      const tagButton = wrapper.findAll('[data-testid="available-tags"] button')[0]
      await tagButton.trigger('click')

      expect(wrapper.emitted('update:modelValue')).toBeTruthy()
      expect(wrapper.emitted('update:modelValue')![0]).toEqual([[]])
    })

    it('adds tag to existing selection', async () => {
      const wrapper = createWrapper({ modelValue: ['tag-1'] })

      const tagButtons = wrapper.findAll('[data-testid="available-tags"] button')
      await tagButtons[1].trigger('click')

      expect(wrapper.emitted('update:modelValue')![0]).toEqual([['tag-1', 'tag-2']])
    })
  })

  describe('remove selected tag', () => {
    it('removes tag when X button clicked', async () => {
      const wrapper = createWrapper({ modelValue: ['tag-1', 'tag-2'] })

      const removeButton = wrapper.find('[data-testid="selected-tags"] button')
      await removeButton.trigger('click')

      expect(wrapper.emitted('update:modelValue')).toBeTruthy()
      expect(wrapper.emitted('update:modelValue')![0]).toEqual([['tag-2']])
    })
  })

  describe('fetching tags', () => {
    it('fetches tags on mount when allTags is empty', async () => {
      storeState = createStoreState({ allTags: [] })

      mount(TagsSelector, {
        props: { modelValue: [] },
      })

      await nextTick()
      expect(storeState.fetchAllTags).toHaveBeenCalled()
    })

    it('does not fetch tags when allTags already populated', () => {
      createWrapper()
      expect(storeState.fetchAllTags).not.toHaveBeenCalled()
    })

    it('does not fetch tags when already loading', async () => {
      storeState = createStoreState({ allTags: [], tagsLoading: true })

      mount(TagsSelector, {
        props: { modelValue: [] },
      })

      await nextTick()
      expect(storeState.fetchAllTags).not.toHaveBeenCalled()
    })
  })

  describe('search filtering', () => {
    it('shows search input when tags exceed 20', () => {
      const manyTags = Array.from({ length: 25 }, (_, i) => ({
        id: `tag-${i}`,
        label: `Tag ${i}`,
      }))

      const wrapper = createWrapper({}, (state) => {
        state.allTags.value = manyTags
      })

      expect(wrapper.find('[data-testid="tag-search-input"]').exists()).toBe(true)
    })

    it('does not show search input when tags are 20 or fewer', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('[data-testid="tag-search-input"]').exists()).toBe(false)
    })

    it('filters tags by search query', async () => {
      const manyTags = Array.from({ length: 25 }, (_, i) => ({
        id: `tag-${i}`,
        label: `Tag ${i}`,
      }))

      const wrapper = createWrapper({}, (state) => {
        state.allTags.value = manyTags
      })

      const searchInput = wrapper.find('[data-testid="tag-search-input"]')
      await searchInput.setValue('Tag 1')
      await nextTick()

      const availableTags = wrapper.find('[data-testid="available-tags"]')
      expect(availableTags.text()).toContain('Tag 1')
      expect(availableTags.text()).toContain('Tag 10')
      expect(availableTags.text()).not.toContain('Tag 2')
    })

    it('shows no results message when search yields nothing', async () => {
      const manyTags = Array.from({ length: 25 }, (_, i) => ({
        id: `tag-${i}`,
        label: `Tag ${i}`,
      }))

      const wrapper = createWrapper({}, (state) => {
        state.allTags.value = manyTags
      })

      const searchInput = wrapper.find('[data-testid="tag-search-input"]')
      await searchInput.setValue('xyz-nonexistent')
      await nextTick()

      expect(wrapper.text()).toContain('problems.tagsSelector.noResults')
    })
  })

  describe('visual states', () => {
    it('marks selected tags with correct aria-pressed', () => {
      const wrapper = createWrapper({ modelValue: ['tag-1'] })

      const buttons = wrapper.findAll('[data-testid="available-tags"] button')
      expect(buttons[0].attributes('aria-pressed')).toBe('true')
      expect(buttons[1].attributes('aria-pressed')).toBe('false')
    })

    it('applies default variant to selected tags', () => {
      const wrapper = createWrapper({ modelValue: ['tag-1'] })

      const selectedBadge = wrapper.findAll('[data-testid="available-tags"] [data-slot="badge"]')[0]
      expect(selectedBadge.classes()).toContain('bg-primary')
    })

    it('applies outline variant to unselected tags', () => {
      const wrapper = createWrapper({ modelValue: [] })

      const unselectedBadge = wrapper.findAll(
        '[data-testid="available-tags"] [data-slot="badge"]',
      )[0]
      expect(unselectedBadge.classes()).not.toContain('bg-primary')
    })
  })

  describe('edge cases', () => {
    it('handles tags that are in modelValue but not in allTags', () => {
      const wrapper = createWrapper({ modelValue: ['tag-1', 'nonexistent-tag'] })

      const selectedTags = wrapper.find('[data-testid="selected-tags"]')
      expect(selectedTags.text()).toContain('Array')
      expect(selectedTags.findAll('[data-slot="badge"]').length).toBe(1)
    })

    it('shows no tags available message when allTags is empty and not loading', () => {
      const wrapper = createWrapper({}, (state) => {
        state.allTags.value = []
        state.tagsLoading.value = false
      })

      expect(wrapper.text()).toContain('problems.tagsSelector.noTagsAvailable')
    })
  })
})
