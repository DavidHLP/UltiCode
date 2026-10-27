import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { Check } from '@lucide/vue'
import LanguageSwitcher from './LanguageSwitcher.vue'

const { setLocale } = vi.hoisted(() => ({ setLocale: vi.fn() }))

vi.mock('@/composables/useLocale', () => ({
  useLocale: () => ({
    availableLocales: [
      { code: 'en', nativeName: 'English' },
      { code: 'zh-CN', nativeName: '简体中文' },
    ],
    setLocale,
    isCurrentLocale: (code: string) => code === 'en',
  }),
}))

describe('LanguageSwitcher', () => {
  it('changes locale on the menu selection event and marks the current locale', async () => {
    // Reka emits select for pointer and keyboard activation. Verify the consumer
    // listens to that public event, without replacing it with a click handler.
    const item = defineComponent({
      emits: ['select'],
      template: '<div><slot /></div>',
    })
    const container = { template: '<div><slot /></div>' }
    const wrapper = mount(LanguageSwitcher, {
      global: {
        mocks: { $t: (key: string) => key },
        stubs: {
          DropdownMenuSub: container,
          DropdownMenuSubTrigger: container,
          DropdownMenuSubContent: container,
          DropdownMenuItem: item,
        },
      },
    })
    const items = wrapper.findAllComponents(item)
    expect(items.map((entry) => entry.text())).toEqual(['English', '简体中文'])
    expect(items[0]!.findComponent(Check).exists()).toBe(true)
    expect(items[1]!.findComponent(Check).exists()).toBe(false)
    items[1]!.vm.$emit('select')
    await wrapper.vm.$nextTick()
    expect(setLocale).toHaveBeenCalledExactlyOnceWith('zh-CN')
  })
})
