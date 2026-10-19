import { shallowMount, type VueWrapper } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DEFAULT_APPEARANCE } from '@ulticode/theme'
import AppearanceSettings from '@ulticode/design-system/AppearanceSettings.vue'

const appearance = {
  settingsOpen: ref(false),
  saved: ref({ ...DEFAULT_APPEARANCE }),
  account: ref(null),
  previewing: ref(false),
  persistenceError: ref(false),
  beginPreview: () => ({ ...DEFAULT_APPEARANCE }),
  preview: vi.fn(),
  cancel: vi.fn(),
  apply: vi.fn(),
}
vi.mock('@ulticode/theme', async (original) => ({
  ...(await original<typeof import('@ulticode/theme')>()),
  useAppearance: () => appearance,
}))

let wrapper: VueWrapper | undefined
const openers: HTMLButtonElement[] = []
function mountSettings() {
  wrapper = shallowMount(AppearanceSettings, {
    global: {
      stubs: {
        Dialog: { template: '<div><slot /></div>' },
        DialogContent: { name: 'DialogContent', template: '<div />', emits: ['closeAutoFocus'] },
      },
    },
  })
  return wrapper
}
function addOpener() {
  const button = document.createElement('button')
  document.body.append(button)
  openers.push(button)
  return button
}
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  appearance.settingsOpen.value = false
  openers.splice(0).forEach((button) => button.remove())
  vi.restoreAllMocks()
})

describe('Appearance dialog focus return', () => {
  it('returns focus to the latest opener without scrolling', async () => {
    const settings = mountSettings()
    for (let attempt = 0; attempt < 2; attempt++) {
      const opener = addOpener()
      opener.focus()
      appearance.settingsOpen.value = true
      await nextTick()
      const focus = vi.spyOn(opener, 'focus')
      appearance.settingsOpen.value = false
      await nextTick()
      const event = new Event('closeAutoFocus', { cancelable: true })
      settings.findComponent({ name: 'DialogContent' }).vm.$emit('closeAutoFocus', event)
      expect(event.defaultPrevented).toBe(true)
      expect(focus).toHaveBeenCalledWith({ preventScroll: true })
    }
  })

  it('leaves the default focus behavior available if the opener was removed', async () => {
    const settings = mountSettings()
    const opener = addOpener()
    opener.focus()
    appearance.settingsOpen.value = true
    await nextTick()
    opener.remove()
    appearance.settingsOpen.value = false
    await nextTick()
    const event = new Event('closeAutoFocus', { cancelable: true })
    settings.findComponent({ name: 'DialogContent' }).vm.$emit('closeAutoFocus', event)
    expect(event.defaultPrevented).toBe(false)
  })
})
