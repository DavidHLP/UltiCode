/**
 * Shared test setup for shared/auth-ui specs.
 *
 * - Installs a real `vue-i18n` plugin with a stub `t()` that returns
 *   the key. Mocking `vue-i18n` via `vi.mock` is brittle because the
 *   library throws when `useI18n()` is called without a plugin
 *   install; using the real plugin is the supported path.
 * - Installs `vue-router` for the same reason — components call
 *   `useRouter()` / `useRoute()` and need a real router instance.
 * - Globally stubs `RouterLink` so logo links render as plain anchors
 *   (we don't need navigation in tests).
 */
import { vi, beforeEach } from "vitest"
import { config } from "@vue/test-utils"
import { createI18n } from "vue-i18n"
import { createMemoryHistory, createRouter } from "vue-router"

export const routerPush = vi.fn()

// Stub vue-router composables — useRouter / useRoute stay real
// (they need a backing router), but RouterLink is replaced by a
// plain anchor stub so we don't pull vue-router's real RouterLink
// implementation into tests. vi.hoisted keeps the stub definition
// available when vi.mock's factory is invoked at hoist time.
const { RouterLinkStub } = vi.hoisted(() => {
  // Vue Options API `this` proxy isn't reliable inside a `render`
  // function when the component is rendered by @vue/test-utils
  // outside its normal lifecycle. Use `setup()` + h() instead: the
  // setup signature gives us props + context (incl. attrs/slots)
  // directly, and h() returns a vnode that Vue's renderer consumes
  // without re-stringifying.
  const { defineComponent, h } = require("vue") as typeof import("vue")
  return {
    RouterLinkStub: defineComponent({
      name: "RouterLink",
      props: ["to"],
      inheritAttrs: false,
      setup(props, { attrs, slots }) {
        return () => {
          const href = typeof props.to === "string" ? props.to : ""
          return h("a", { href, ...attrs }, slots.default?.())
        }
      },
    }),
  }
})
vi.mock("vue-router", async () => {
  const actual = await vi.importActual<typeof import("vue-router")>("vue-router")
  return {
    ...actual,
    RouterLink: RouterLinkStub,
  }
})

// Stub @ulticode/theme so AuthThemeToggle doesn't pull in real DOM
// (window.matchMedia + localStorage) and works under jsdom.
vi.mock("../../../theme/src", () => ({
  cycleTheme: vi.fn(),
  useColorTheme: () => ({
    theme: { value: "system" },
  }),
}))

// vue-i18n plugin: empty messages, t() returns the key. Installed on
// each wrapper's globalProperties via @vue/test-utils `global.plugins`.
export const i18n = createI18n({
  legacy: false,
  locale: "en-US",
  fallbackLocale: "en-US",
  messages: { "en-US": {}, "zh-CN": {} },
})

// Default router instance. Tests can override via `router` option in
// `mount()`. `push` is recorded so we can assert redirects.
export const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: "/", component: { template: "<div />" } },
    { path: "/landing", component: { template: "<div />" } },
    { path: "/dashboard", component: { template: "<div />" } },
    { path: "/welcome", component: { template: "<div />" } },
  ],
})

// Track `router.push` calls for redirect assertions. We attach to the
// router instance directly rather than vi.mocking push.
const originalPush = router.push.bind(router)
router.push = vi.fn((...args: unknown[]) => {
  routerPush(...(args as Parameters<typeof router.push>))
  return originalPush(...(args as Parameters<typeof router.push>))
}) as typeof router.push

// Global Vue component stubs. These take effect for every component
// mounted via @vue/test-utils in this suite. The RouterLink stub is
// the same instance used in the vi.mock factory above.
config.global.stubs = {
  RouterLink: RouterLinkStub,
  RouterView: { template: "<div />" },
}
config.global.plugins = [i18n, router]

beforeEach(() => {
  routerPush.mockClear()
})