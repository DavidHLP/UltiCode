<script setup lang="ts">
/**
 * AuthLayout - Two-pane auth shell (form-side + pattern-side)
 *
 * Slots:
 * - `form` — the actual auth form (LoginForm, RegisterForm, etc.).
 *   Consumer wraps it in an `<AuthCard>` first if a card frame is needed.
 * - `pattern` — the right-side panel (typically an `<AuthPatternBackground>`).
 *   When omitted, the right pane is hidden (e.g. on a narrow viewport).
 *
 * The UltiCode logo + theme toggle + status footer are baked in. The
 * consumer customizes them via props (`badge`, `version`, `statusText`,
 * `homeHref`).
 */
import { Terminal } from "lucide-vue-next";
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import AuthThemeToggle from "../components/AuthThemeToggle.vue";
import type { AuthLayoutProps } from "./useAuthLayout";

defineOptions({
  name: "AuthLayout",
});

const props = withDefaults(defineProps<AuthLayoutProps>(), {
  badge: "CODE",
  version: "v2.0.0",
  statusText: "",
  hidePattern: false,
  homeHref: "/",
});

const { t } = useI18n();
// Reactive: parent re-renders that change `statusText` after setup must
// update the footer (see review L3). `computed` keeps the i18n fallback
// lazy and prop-reactive in one expression.
const statusText = computed(
  () => props.statusText || t("auth.layout.systemOnline"),
);
</script>

<template>
  <div class="auth-layout">
    <!-- Left Side - Form Area -->
    <div class="auth-layout__form-side">
      <div class="auth-layout__header">
        <RouterLink :to="homeHref" class="auth-logo">
          <div class="auth-logo__icon">
            <Terminal class="size-4" />
          </div>
          <div class="auth-logo__text-group">
            <span class="auth-logo__text">UltiCode</span>
            <span class="auth-logo__badge">{{ badge }}</span>
          </div>
        </RouterLink>
        <AuthThemeToggle />
      </div>

      <div class="auth-layout__content">
        <slot name="form" />
      </div>

      <div class="auth-layout__footer">
        <span class="auth-layout__version">{{ version }}</span>
        <span class="auth-layout__separator">|</span>
        <span class="auth-layout__status">
          <span class="auth-layout__status-dot"></span>
          {{ statusText }}
        </span>
      </div>
    </div>

    <!-- Right Side - Pattern Panel -->
    <slot v-if="!hidePattern" name="pattern" />
  </div>
</template>

<style scoped>
.auth-layout {
  display: grid;
  min-height: 100vh;
  min-height: 100svh;
  grid-template-columns: 1fr;
  background: var(--background);
}

@media (min-width: 1024px) {
  .auth-layout {
    grid-template-columns: 1.1fr 0.9fr;
    height: 100vh;
    height: 100svh;
    overflow: hidden;
  }
}

.auth-layout__form-side {
  display: flex;
  flex-direction: column;
  padding: 1rem 1.5rem;
}

@media (min-width: 768px) {
  .auth-layout__form-side {
    padding: 1.5rem 2.5rem;
  }
}

@media (min-width: 1024px) {
  .auth-layout__form-side {
    height: 100%;
    overflow: hidden;
    justify-content: space-between;
    padding: 1.5rem 3rem;
    border-right: 1px solid var(--border);
  }
}

.auth-layout__header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
}

@media (min-width: 1024px) {
  .auth-layout__header {
    justify-content: flex-start;
  }
}

.auth-logo {
  display: flex;
  align-items: center;
  gap: 0.875rem;
  padding: 0.5rem 0.75rem;
  text-decoration: none;
  color: var(--foreground);
  border: 1px solid var(--border-subtle);
  border-radius: 0;
  background: var(--background);
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast);
}

.auth-logo:hover {
  border-color: var(--border-subtle);
  box-shadow: 0 2px 12px color-mix(in srgb, var(--shadow-color) 5%, transparent);
}

.dark .auth-logo:hover {
  border-color: var(--foreground-muted);
  box-shadow: 0 2px 12px color-mix(in srgb, var(--shadow-color) 20%, transparent);
}

.auth-logo__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  background: var(--primary);
  color: var(--primary-foreground);
  border-radius: 0;
  transition: box-shadow var(--transition-fast);
}

.auth-logo:hover .auth-logo__icon {
  box-shadow: 0 0 12px var(--accent-glow);
}

.auth-logo__text-group {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.auth-logo__text {
  font-size: var(--uc-text-md);
  font-weight: var(--uc-font-weight-semibold);
  letter-spacing: var(--uc-tracking-normal);
  color: var(--foreground);
}

.auth-logo__badge {
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-2xs);
  font-weight: var(--uc-font-weight-semibold);
  letter-spacing: 0.15em;
  color: var(--foreground-muted);
  text-transform: uppercase;
}

.auth-layout__content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  padding: 0.5rem 0;
}

@media (min-width: 768px) {
  .auth-layout__content {
    padding: 0.5rem 0;
  }
}

.auth-layout__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding-top: 1rem;
  font-family: var(--uc-font-code);
  font-size: var(--uc-type-code-size);
  color: var(--foreground-muted);
}

.auth-layout__separator {
  opacity: 0.4;
}

.auth-layout__status {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.auth-layout__status-dot {
  width: 0.375rem;
  height: 0.375rem;
  background: var(--status-success-mark);
  border-radius: 50%;
  animation: pulse-dot 2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}
</style>