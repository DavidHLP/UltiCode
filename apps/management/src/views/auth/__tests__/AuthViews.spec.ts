import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { shallowMount } from '@vue/test-utils'
import LoginView from '../LoginView.vue'
import SignupView from '../SignupView.vue'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ login: vi.fn() }) }))

const formStub = defineComponent({
  props: { hideOAuth: Boolean, hideForgot: Boolean, hideSignUp: Boolean },
  template: '<form />',
})

const wrappers: ReturnType<typeof shallowMount>[] = []
afterEach(() => {
  wrappers.forEach((wrapper) => wrapper.unmount())
  wrappers.length = 0
})

describe('management auth shells', () => {
  it.each([
    ['login', LoginView, 'LoginForm'],
    ['registration placeholder', SignupView, 'RegisterForm'],
  ] as const)('hides OAuth in the %s shell', (_, component, formName) => {
    const wrapper = shallowMount(component, {
      global: {
        stubs: {
          AuthLayout: { template: '<div><slot name="form" /></div>' },
          AuthCard: { template: '<div><slot /></div>' },
          [formName]: formStub,
        },
      },
    })
    wrappers.push(wrapper)
    expect(wrapper.findComponent(formStub).props('hideOAuth')).toBe(true)
    if (formName === 'LoginForm') {
      expect(wrapper.findComponent(formStub).props('hideForgot')).toBe(true)
      expect(wrapper.findComponent(formStub).props('hideSignUp')).toBe(true)
    }
  })
})
