import { mount } from '@vue/test-utils'
import { ref, type Component } from 'vue'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import GeneralSettings from './GeneralSettings.vue'
import EmailSettings from './EmailSettings.vue'
import FeatureToggleSettings from './FeatureToggleSettings.vue'

vi.mock('@ulticode/theme', () => ({
  useColorTheme: () => ({ theme: ref('system'), setTheme: vi.fn() }),
}))

function mountSettings(component: Component, settings: Record<string, string | boolean>) {
  return mount(component, {
    props: { settings },
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
}

const cases: {
  name: string
  component: Component
  key: string
  settings: Record<string, string | boolean>
}[] = [
  {
    name: 'general',
    component: GeneralSettings,
    key: 'enable_registrations',
    settings: {
      site_name: '',
      site_description: '',
      maintenance_message: '',
      enable_registrations: true,
      require_email_verification: true,
      maintenance_mode: true,
    },
  },
  {
    name: 'email',
    component: EmailSettings,
    key: 'smtp_secure',
    settings: {
      smtp_host: '',
      smtp_port: '',
      smtp_user: '',
      smtp_password: '',
      smtp_from: '',
      smtp_from_name: '',
      smtp_secure: true,
    },
  },
  {
    name: 'features',
    component: FeatureToggleSettings,
    key: 'feature_contest',
    settings: {
      feature_contest: true,
      feature_forum: true,
      feature_solutions: true,
      feature_subscriptions: true,
      feature_achievements: true,
      feature_notifications: true,
      feature_bookmarks: true,
      feature_problem_lists: true,
      feature_agent: true,
    },
  },
]

describe('Settings switch bindings', () => {
  it.each(cases)(
    'renders and updates the real $name switches without mutating input',
    async ({ component, settings, key }) => {
      const wrapper = mountSettings(component, settings)
      const switches = wrapper.findAll('[role="switch"]')
      expect(switches.length).toBeGreaterThan(0)
      for (const control of switches) expect(control.attributes('aria-checked')).toBe('true')

      await switches[0].trigger('click')
      expect(wrapper.emitted('update:settings')).toEqual([[{ [key]: false }]])
      await wrapper.setProps({ settings: { ...settings, [key]: false } })
      expect(switches[0].attributes('aria-checked')).toBe('false')

      await switches[0].trigger('click')
      expect(wrapper.emitted('update:settings')?.at(-1)).toEqual([{ [key]: true }])
      expect(settings[key]).toBe(true)
      wrapper.unmount()
    },
  )
})
