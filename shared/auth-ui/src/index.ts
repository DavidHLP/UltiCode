// ---------------------------------------------------------------------------
// @ulticode/auth-ui — public API
//
// Vue UI components + view shells shared between UltiCode console (9002)
// and management (9003). The non-UI logic (cookie/csrf/state/permission/
// refresh) lives in @ulticode/auth-core; this package is the visual layer
// only and depends on the consumer's app store + i18n instance at runtime.
//
// Conventions:
// - All components are pure UI; they never call Pinia directly. Pass the
//   store reference (or callback props) from the consuming app.
// - i18n keys are namespaced under `auth.*` and `common.appearance.*`.
//   Consumers must define both locales (en-US, zh-CN).
// ---------------------------------------------------------------------------

// Components — primitives (no store, no API)
export { default as AuthButton } from './components/AuthButton.vue'
export { default as AuthCard } from './components/AuthCard.vue'
export { default as AuthDivider } from './components/AuthDivider.vue'
export { default as AuthGrid } from './components/AuthGrid.vue'
export { default as AuthInput } from './components/AuthInput.vue'
export { default as AuthThemeToggle } from './components/AuthThemeToggle.vue'
export { default as OAuthButton } from './components/OAuthButton.vue'

// Components — form (require consumer to inject submit handler / store)
export { default as LoginForm } from './components/LoginForm.vue'
export { default as RegisterForm } from './components/RegisterForm.vue'

// Layouts — view shells (badge text + form content + right-side pattern are slots/props)
export { default as AuthLayout } from './layouts/AuthLayout.vue'
export { default as AuthPatternBackground } from './layouts/AuthPatternBackground.vue'
export type { AuthPatternLine } from './layouts/useAuthLayout'

// Utilities
export { cn } from './components/cn'