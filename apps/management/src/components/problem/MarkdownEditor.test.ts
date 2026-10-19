import { mount, type VueWrapper } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MarkdownEditor from './MarkdownEditor.vue'

let wrapper: VueWrapper | undefined

function mountEditor() {
  wrapper = mount(MarkdownEditor, {
    props: { modelValue: 'hello' },
    global: {
      plugins: [
        createI18n({
          legacy: false,
          locale: 'en',
          messages: { en: {} },
          missingWarn: false,
          fallbackWarn: false,
        }),
      ],
    },
  })
  return wrapper
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  vi.clearAllTimers()
  vi.useRealTimers()
})

describe('Markdown editor native controls', () => {
  it('formats the native selection without duplicating the rest of the text', async () => {
    vi.useFakeTimers()
    const editor = mountEditor()
    const textarea = editor.get('textarea').element as HTMLTextAreaElement
    textarea.setSelectionRange(1, 4)

    await editor.get('button[title="problems.markdownEditor.bold"]').trigger('click')
    expect(editor.emitted('update:modelValue')?.at(-1)).toEqual(['h**ell**o'])
    await vi.runAllTimersAsync()
    expect(textarea.selectionStart).toBe(3)
    expect(textarea.selectionEnd).toBe(6)
  })

  it('synchronizes the actual editor and preview scroll elements in both directions', async () => {
    const editor = mountEditor()
    const textarea = editor.get('textarea').element as HTMLTextAreaElement
    const preview = editor.get('.preview-content').element as HTMLDivElement
    Object.defineProperties(textarea, { scrollHeight: { value: 500 }, clientHeight: { value: 100 } })
    Object.defineProperties(preview, { scrollHeight: { value: 300 }, clientHeight: { value: 100 } })

    textarea.scrollTop = 200
    await editor.get('textarea').trigger('scroll')
    expect(preview.scrollTop).toBe(100)

    preview.scrollTop = 50
    await editor.get('.preview-content').trigger('scroll')
    expect(textarea.scrollTop).toBe(100)
  })

  it('keeps a finite offset when the source has no scroll range', async () => {
    const editor = mountEditor()
    const textarea = editor.get('textarea').element as HTMLTextAreaElement
    const preview = editor.get('.preview-content').element as HTMLDivElement
    Object.defineProperties(textarea, { scrollHeight: { value: 100 }, clientHeight: { value: 100 } })
    Object.defineProperties(preview, { scrollHeight: { value: 300 }, clientHeight: { value: 100 } })
    preview.scrollTop = 50

    await editor.get('textarea').trigger('scroll')
    expect(preview.scrollTop).toBe(0)
  })
})
