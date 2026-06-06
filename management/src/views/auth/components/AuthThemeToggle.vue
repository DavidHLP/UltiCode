<script setup lang="ts">
/**
 * AuthThemeToggle - Compact theme toggle button for management auth pages.
 * Mirrors the console implementation but pulls translations from the
 * `settings.appearance.*` namespace (management uses a different i18n
 * layout). Uses the shared `@ulticode/theme` singleton so all theme
 * pickers in this app stay in sync.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Sun, Moon, Monitor } from 'lucide-vue-next'
import { cycleTheme, useColorTheme } from '@/composables/useTheme'

defineOptions({
  name: 'AuthThemeToggle',
})

const { t } = useI18n()
const { theme: themeRef } = useColorTheme()
// vue-tsc 3.x does not auto-unwrap `Ref<T>` in template comparisons or
// function arguments; expose the value as a `ComputedRef` to match the
// project convention (see `useLocale`).
const theme = computed(() => themeRef.value)

const labelFor = (mode: 'light' | 'dark' | 'system'): string => {
  // Fall back to the enum key if the translation is missing.
  return t(`settings.appearance.${mode}`, mode)
}
</script>

<template>
  <button
    type="button"
    class="auth-theme-toggle"
    :title="labelFor(theme)"
    :aria-label="labelFor(theme)"
    @click="cycleTheme"
  >
    <Sun v-if="theme === 'light'" class="auth-theme-toggle__icon" />
    <Moon v-else-if="theme === 'dark'" class="auth-theme-toggle__icon" />
    <Monitor v-else class="auth-theme-toggle__icon" />
    <span class="sr-only">{{ labelFor(theme) }}</span>
  </button>
</template>

<style scoped>
.auth-theme-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border: 1px solid var(--border);
  border-radius: 0;
  background: var(--card);
  color: var(--silver-500);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.auth-theme-toggle:hover {
  border-color: var(--accent-electric);
  color: var(--accent-electric);
  box-shadow: 2px 2px 0px 0px var(--accent-electric);
  transform: translate(-1px, -1px);
}

.auth-theme-toggle:active {
  transform: translate(1px, 1px);
  box-shadow: none;
}

.auth-theme-toggle:focus-visible {
  outline: 2px solid var(--accent-electric);
  outline-offset: 1px;
}

.auth-theme-toggle__icon {
  width: 0.875rem;
  height: 0.875rem;
}
</style>
