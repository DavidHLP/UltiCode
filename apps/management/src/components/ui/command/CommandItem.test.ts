import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, h } from 'vue'
import Command from './Command.vue'
import CommandGroup from './CommandGroup.vue'
import CommandItem from './CommandItem.vue'

describe('CommandItem', () => {
  it('uses theme-aware highlighted colors', () => {
    const Harness = defineComponent({
      setup() {
        return () =>
          h(Command, null, {
            default: () =>
              h(
                CommandGroup,
                { heading: 'Navigation' },
                {
                  default: () => h(CommandItem, { value: 'dashboard' }, () => 'Dashboard'),
                },
              ),
          })
      },
    })
    const wrapper = mount(Harness)

    const classes = wrapper.get('[data-slot="command-item"]').classes()

    expect(classes).toContain(
      'data-[highlighted]:bg-[color-mix(in_oklch,_var(--primary)_10%,_var(--card))]',
    )
    expect(classes).toContain(
      'dark:data-[highlighted]:bg-[color-mix(in_oklch,_var(--primary)_14%,_var(--card))]',
    )
    expect(classes).toContain('data-[highlighted]:text-[var(--primary)]')
    expect(classes).not.toContain('data-[highlighted]:bg-accent')
  })
})
