<script setup lang="ts">
/**
 * RegisterView - Registration page with AuthGrid + AuthCard
 */
import { Terminal } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import RegisterForm from "@/views/auth/components/RegisterForm.vue";
import AuthCard from "@/views/auth/components/AuthCard.vue";
import AuthGrid from "@/views/auth/components/AuthGrid.vue";
import AuthThemeToggle from "@/views/auth/components/AuthThemeToggle.vue";

const { t } = useI18n();

defineOptions({
  name: "RegisterView",
});
</script>

<template>
  <div class="auth-layout">
    <!-- Left Side - Form Area -->
    <div class="auth-layout__form-side">
      <div class="auth-layout__header">
        <RouterLink to="/" class="auth-logo">
          <div class="auth-logo__icon">
            <Terminal class="size-4" />
          </div>
          <div class="auth-logo__text-group">
            <span class="auth-logo__text">UltiCode</span>
            <span class="auth-logo__badge">CODE</span>
          </div>
        </RouterLink>
        <AuthThemeToggle />
      </div>

      <div class="auth-layout__content">
        <AuthCard :title="t('auth.register.terminal')">
          <RegisterForm />
        </AuthCard>
      </div>

      <div class="auth-layout__footer">
        <span class="auth-layout__version">v2.0.0</span>
        <span class="auth-layout__separator">|</span>
        <span class="auth-layout__status">
          <span class="auth-layout__status-dot"></span>
          {{ t("auth.layout.systemOnline") }}
        </span>
      </div>
    </div>

    <!-- Right Side - Grid Background -->
    <AuthGrid>
      <div class="auth-pattern-text">
        <div class="auth-pattern-text__prefix">$</div>
        <h2 class="auth-pattern-text__title whitespace-pre-line">
          {{ t("auth.layout.codingConsole") }}
        </h2>
        <p class="auth-pattern-text__subtitle">
          {{ t("auth.layout.codingConsoleSubtitle") }}
        </p>
        <div class="auth-pattern-text__cursor"></div>

        <!-- Terminal Status Spec Block -->
        <div class="auth-pattern-terminal select-none">
          <div class="auth-pattern-terminal__header">
            <span
              class="auth-pattern-terminal__dot bg-[var(--terminal-red)]"
            ></span>
            <span
              class="auth-pattern-terminal__dot bg-[var(--terminal-amber)]"
            ></span>
            <span
              class="auth-pattern-terminal__dot bg-[var(--terminal-green)]"
            ></span>
            <span class="auth-pattern-terminal__title">system_status.sh</span>
          </div>
          <div class="auth-pattern-terminal__content font-mono text-xs">
            <div
              class="text-[var(--solarized-base01)] dark:text-[var(--silver-500)]"
            >
              $ systemctl status ulticode.service
            </div>
            <div class="text-[var(--terminal-green)] font-bold">
              ● ulticode.service - UltiCode Platform
            </div>
            <div class="pl-4">
              Active:
              <span class="text-[var(--terminal-green)] font-bold"
                >active (running)</span
              >
              since Jun 2026
            </div>
            <div class="pl-4">PID: 9002 (vite-console)</div>
            <div
              class="text-[var(--solarized-base01)] dark:text-[var(--silver-500)] mt-2.5"
            >
              $ npx vitest run --coverage
            </div>
            <div class="text-[var(--terminal-green)]">
              ✓ 154 tests passed (100% coverage)
            </div>
            <div
              class="text-[var(--solarized-base01)] dark:text-[var(--silver-500)] mt-2.5"
            >
              $ check_db_connection
            </div>
            <div>
              Database:
              <span class="text-[var(--terminal-green)] font-bold"
                >mysql@localhost (CONNECTED)</span
              >
            </div>
            <div>
              Server Port:
              <span class="text-[var(--accent-electric)] font-bold"
                >9002 (Vite Console)</span
              >
            </div>
          </div>
        </div>
      </div>
    </AuthGrid>
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
  border: 1px solid var(--silver-200);
  border-radius: 0;
  background: var(--background);
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast);
}

.auth-logo:hover {
  border-color: var(--silver-300);
  box-shadow: 0 2px 12px oklch(0 0 0 / 0.05);
}

.dark .auth-logo:hover {
  border-color: var(--silver-400);
  box-shadow: 0 2px 12px oklch(0 0 0 / 0.2);
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
  font-weight: var(--uc-font-weight-semibold)
  letter-spacing: var(--uc-tracking-normal);
  color: var(--foreground);
}

.auth-logo__badge {
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-2xs);
  font-weight: var(--uc-font-weight-semibold)
  letter-spacing: 0.15em;
  color: var(--silver-500);
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
  color: var(--silver-400);
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
  background: var(--terminal-green);
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

.auth-pattern-text {
  padding: 2.5rem;
  text-align: left;
}

.auth-pattern-text__prefix {
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-xl);
  color: var(--accent-primary);
  opacity: 0.8;
  margin-bottom: 0.5rem;
}

.auth-pattern-text__title {
  font-size: var(--uc-text-3xl);
  font-weight: var(--uc-font-weight-bold)
  letter-spacing: var(--uc-tracking-normal);
  line-height: 1.2;
  margin-bottom: 1rem;
  color: var(--solarized-base03);
}

.dark .auth-pattern-text__title {
  color: var(--silver-900);
}

.auth-pattern-text__subtitle {
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-sm);
  color: var(--solarized-base00);
  letter-spacing: var(--uc-tracking-normal);
  line-height: 1.5;
}

.dark .auth-pattern-text__subtitle {
  color: var(--solarized-base0);
}

.auth-pattern-text__cursor {
  display: inline-block;
  width: 0.5rem;
  height: 1.25rem;
  background: var(--accent-primary);
  margin-left: 0.25rem;
  margin-top: 0.5rem;
  animation: blink 1s step-end infinite;
  box-shadow: 0 0 8px var(--accent-glow);
}


.auth-pattern-terminal {
  margin-top: 2.5rem;
  border: 1px solid var(--border);
  background: var(--card);
  width: 100%;
  max-width: 28rem;
  box-shadow: 3px 3px 0px 0px var(--border);
}

.auth-pattern-terminal__header {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.5rem 0.75rem;
  background: var(--surface-sunken);
  border-bottom: 1px solid var(--border);
}

.dark .auth-pattern-terminal__header {
  background: var(--surface-sunken);
}

.auth-pattern-terminal__dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
}

.auth-pattern-terminal__title {
  font-family: var(--uc-font-code);
  font-size: var(--uc-type-code-size);
  color: var(--solarized-base01);
  margin-left: 0.5rem;
}

.dark .auth-pattern-terminal__title {
  color: var(--silver-400);
}

.auth-pattern-terminal__content {
  padding: 1rem;
  line-height: 1.6;
  color: var(--solarized-base00);
}

.dark .auth-pattern-terminal__content {
  color: var(--silver-400);
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}
</style>
