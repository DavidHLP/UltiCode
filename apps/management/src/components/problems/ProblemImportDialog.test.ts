import { shallowMount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import ProblemImportDialog from './ProblemImportDialog.vue'

describe('problem import dialog layout', () => {
  it('keeps viewport gutters and uses whole-dialog scrolling without negative margins', () => {
    const wrapper = shallowMount(ProblemImportDialog, {
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
      expect(dialog.classes()).toContain('sm:max-w-2xl')
      expect(dialog.classes()).not.toContain('max-w-2xl')
      expect(dialog.classes()).not.toContain('overflow-hidden')
      const content = wrapper.get('.space-y-6')
      expect(content.classes()).not.toContain('overflow-y-auto')
      expect(content.classes()).not.toContain('-mx-6')
      expect(content.classes()).not.toContain('px-6')
    } finally {
      wrapper.unmount()
    }
  })
})
