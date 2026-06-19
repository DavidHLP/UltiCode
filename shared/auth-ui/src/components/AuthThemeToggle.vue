<script setup lang="ts">
/**
 * AuthThemeToggle - Compact theme toggle button for auth pages
 *
 * Cycles through light → dark → system modes on click. Uses the shared
 * `@ulticode/theme` singleton (re-exported via shared/auth-ui's relative
 * import path) so other theme pickers (header dropdown, settings card)
 * stay in sync across both frontends.
 *
 * i18n keys: `common.appearance.{light,dark,system}`. Both apps must
 * define these under their `common` locale namespace.
 */
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { Sun, Moon, Monitor } from "lucide-vue-next";
import { cycleTheme, useColorTheme } from "../../../theme/src";

defineOptions({
  name: "AuthThemeToggle",
});

const { t } = useI18n();
const { theme: themeRef } = useColorTheme();
// vue-tsc 3.x does not auto-unwrap `Ref<T>` in template comparisons or
// function arguments; project convention is to expose reactive values as
// `ComputedRef` (see `useLocale`). Match that here.
const theme = computed(() => themeRef.value);

const labelFor = (mode: "light" | "dark" | "system"): string => {
  // Falls back to the mode key if the translation is missing so the
  // a11y label is never empty.
  return t(`common.appearance.${mode}`, mode);
};
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