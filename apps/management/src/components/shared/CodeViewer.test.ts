import { mount, type VueWrapper } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { afterEach, describe, expect, it } from 'vitest'
import CodeViewer from './CodeViewer.vue'

const languages = [
  { id: 'cpp', language: 'C++', code: 'int answer = 42;' },
  { id: 'java', language: 'Java', code: 'int value = 7;' },
]
let wrapper: VueWrapper | undefined

function mountViewer(props: Record<string, unknown> = {}) {
  wrapper = mount(CodeViewer, {
    props,
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
})

describe('Code viewer language selection', () => {
  it('selects the first available language when options arrive asynchronously', async () => {
    const viewer = mountViewer()
    await viewer.setProps({ languages })
    expect(viewer.get('code').text()).toBe(languages[0].code)
  })

  it('preserves a selected language on refresh and falls back when it is removed', async () => {
    const viewer = mountViewer({ languages, language: 'C++' })
    await viewer
      .findAll('button')
      .find((button) => button.text() === 'Java')!
      .trigger('click')
    await viewer.setProps({ languages: [...languages].reverse() })
    expect(viewer.get('code').text()).toBe(languages[1].code)
    await viewer.setProps({ languages: [languages[0]] })
    expect(viewer.get('code').text()).toBe(languages[0].code)
  })

  it('honors an available initial language and leaves single-language code intact', async () => {
    const viewer = mountViewer({ languages, language: 'java' })
    expect(viewer.get('code').text()).toBe(languages[1].code)
    await viewer.setProps({
      languages: [],
      code: 'print(42)',
      language: 'Python',
      showLanguageSelector: false,
    })
    expect(viewer.get('code').text()).toBe('print(42)')
  })
})
