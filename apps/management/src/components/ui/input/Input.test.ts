import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import Input from './Input.vue'

describe('Input terminal variant', () => {
  it('uses theme-aware terminal surfaces, focus, and text selection colors', () => {
    const wrapper = mount(Input, {
      props: {
        variant: 'terminal',
        modelValue: 'Selected text',
      },
    })

    const classes = wrapper.get('input').classes()

    expect(classes).toContain('bg-[var(--surface-sunken)]')
    expect(classes).toContain('selection:bg-[var(--primary)]')
    expect(classes).toContain('selection:text-primary-foreground')
    expect(classes).toContain('focus-visible:border-[var(--primary)]')
  })
})
