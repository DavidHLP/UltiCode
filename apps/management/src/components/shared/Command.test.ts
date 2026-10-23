import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { defineComponent, type PropType } from 'vue'
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@ulticode/design-system/ui/command'

const SearchResults = defineComponent({
  components: { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList },
  props: { items: { type: Array as PropType<string[]>, required: true } },
  template: `
    <Command>
      <CommandInput placeholder="Search" />
      <CommandList>
        <CommandEmpty>No results</CommandEmpty>
        <CommandGroup v-if="items.length">
          <CommandItem v-for="item in items" :key="item" :value="item">{{ item }}</CommandItem>
        </CommandGroup>
      </CommandList>
    </Command>
  `,
})

let wrapper: VueWrapper | undefined
const originalScroll = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollIntoView')
beforeAll(() => {
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: vi.fn() })
})
afterAll(() => {
  if (originalScroll) Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', originalScroll)
  else delete (HTMLElement.prototype as Partial<HTMLElement>).scrollIntoView
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('Command result updates', () => {
  it('shows a matching group arriving after the query without typing again', async () => {
    wrapper = mount(SearchResults, { props: { items: [] } })
    await wrapper.get('input').setValue('needle')
    await flushPromises()
    expect(wrapper.get('[data-slot="command-empty"]').isVisible()).toBe(true)

    await wrapper.setProps({ items: ['Needle result'] })
    await flushPromises()
    expect(wrapper.get('[data-slot="command-group"]').isVisible()).toBe(true)
    expect(wrapper.get('[data-slot="command-item"]').text()).toBe('Needle result')
    expect(wrapper.find('[data-slot="command-empty"]').exists()).toBe(false)
  })

  it('shows the empty state when the last matching result is removed', async () => {
    wrapper = mount(SearchResults, { props: { items: ['Needle result', 'Other result'] } })
    await flushPromises()
    await wrapper.get('input').setValue('needle')
    await wrapper.setProps({ items: ['Other result'] })
    await flushPromises()
    expect(wrapper.get('[data-slot="command-empty"]').isVisible()).toBe(true)
    expect(wrapper.get('[data-slot="command-group"]').isVisible()).toBe(false)
  })

  it('keeps local filtering and restores items when the query is cleared', async () => {
    wrapper = mount(SearchResults, { props: { items: ['Needle result', 'Other result'] } })
    await flushPromises()
    await wrapper.get('input').setValue('needle')
    expect(wrapper.findAll('[data-slot="command-item"]').map((item) => item.text())).toEqual([
      'Needle result',
    ])
    await wrapper.get('input').setValue('')
    expect(wrapper.findAll('[data-slot="command-item"]')).toHaveLength(2)
  })
})
