<script setup lang="ts">
/**
 * AuthThemeToggle - Theme switch for the management auth pages.
 *
 * Visually paired with the sibling `.auth-logo` card so the page header
 * reads as one intentional bar (logo on the left, theme switch on the
 * right) instead of a 32px square floating awkwardly next to a much
 * larger card. The previous neo-brutalist hover (translate -1px,-1px
 * with a hard offset shadow) is replaced with a calm background + border
 * swap that fits the terminal-card design language defined by the
 * `solarized-terminal-design-style` skill.
 *
 * Pulls translations from `settings.appearance.*` and uses the shared
 * `@ulticode/theme` singleton so all theme pickers in this app stay in
 * sync.
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
    <span class="auth-theme-toggle__label">{{ labelFor(theme) }}</span>
  </button>
</template>

<style scoped>

.auth-theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;

  height: calc(2rem + 0.5rem * 2);
  padding: 0 0.75rem;
  border: 1px solid var(--border);
  border-radius: 0;
  background: var(--background);
  color: var(--silver-500);
  cursor: pointer;
  font-family: var(--uc-font-code);
  font-size: var(--uc-type-code-size);
  font-weight: var(--uc-font-weight-medium);
  letter-spacing: var(--uc-tracking-terminal);
  text-transform: uppercase;
  transition:
    border-color var(--transition-fast),
    background-color var(--transition-fast),
    color var(--transition-fast),
    box-shadow var(--transition-fast);
}


.auth-theme-toggle:hover {
  border-color: var(--accent-electric);
  color: var(--accent-electric);
  background: color-mix(in oklch, var(--accent-electric) 6%, transparent);
}

.auth-theme-toggle:active {
  background: color-mix(in oklch, var(--accent-electric) 12%, transparent);
}

.auth-theme-toggle:focus-visible {
  outline: none;
  border-color: var(--accent-electric);
  box-shadow: 0 0 0 2px var(--accent-electric-glow);
}


.auth-theme-toggle__icon {
  width: 1rem;
  height: 1rem;
  flex-shrink: 0;
}

.auth-theme-toggle__label {
  line-height: 1;
}
</style>
