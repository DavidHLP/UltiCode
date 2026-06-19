<script setup lang="ts">
/**
 * AuthPatternBackground - Right-side grid + terminal spec panel
 *
 * The "wall of code" pattern that fills the right half of the auth page on
 * desktop. The headline + subtitle are passed as props (i18n strings from
 * the consumer). The bottom `system_status.sh` block uses the `spec` prop
 * so each app can show its own port + test counts without forking the
 * layout.
 *
 * On screens narrower than 1024px the AuthGrid collapses (display:none)
 * and this panel is hidden. Mobile users see only the form side.
 */
import { useI18n } from "vue-i18n";
import AuthGrid from "../components/AuthGrid.vue";
import type { AuthPatternLine } from "./useAuthLayout";

defineOptions({
  name: "AuthPatternBackground",
});

withDefaults(
  defineProps<{
    /** Pre-title prefix rendered in code font (e.g. "$"). Defaults to "$". */
    prefix?: string;
    /** Main headline (i18n string). Supports newlines via `whitespace-pre-line`. */
    title: string;
    /** Subheadline (i18n string). */
    subtitle?: string;
    /** Status window title shown in the panel header. Defaults to "system_status.sh". */
    windowTitle?: string;
    /** Custom status lines; falls back to a generic placeholder if empty. */
    spec?: AuthPatternLine[];
  }>(),
  {
    prefix: "$",
    subtitle: "",
    windowTitle: "system_status.sh",
    spec: () => [],
  },
);

const { t } = useI18n();

function outputClass(
  tone: 'normal' | 'success' | 'accent' | 'muted' | undefined,
): string {
  switch (tone) {
    case 'success':
      return 'text-[var(--terminal-green)] font-bold';
    case 'accent':
      return 'text-[var(--accent-electric)] font-bold';
    case 'muted':
      return 'text-[var(--solarized-base01)] dark:text-[var(--silver-500)]';
    default:
      return 'text-[var(--solarized-base00)] dark:text-[var(--silver-400)]';
  }
}

function renderOutput(line: string | { text: string; tone?: 'normal' | 'success' | 'accent' | 'muted' }) {
  return typeof line === 'string' ? { text: line, tone: 'normal' as const } : line;
}
</script>

<template>
  <AuthGrid>
    <div class="auth-pattern-text">
      <div class="auth-pattern-text__prefix">{{ prefix }}</div>
      <h2 class="auth-pattern-text__title whitespace-pre-line">
        {{ title }}
      </h2>
      <p v-if="subtitle" class="auth-pattern-text__subtitle">
        {{ subtitle }}
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
          <span class="auth-pattern-terminal__title">{{ windowTitle }}</span>
        </div>
        <div class="auth-pattern-terminal__content font-mono text-xs">
          <template v-for="(line, idx) in spec" :key="idx">
            <div
              v-if="line.prompt"
              class="text-[var(--solarized-base01)] dark:text-[var(--silver-500)]"
            >
              $ {{ line.prompt }}
            </div>
            <div :class="outputClass(renderOutput(line.output).tone)">
              {{ renderOutput(line.output).text }}
            </div>
          </template>
          <div
            v-if="!spec.length"
            class="text-[var(--solarized-base01)] dark:text-[var(--silver-500)]"
          >
            {{ t("auth.layout.systemOnline") }}
          </div>
        </div>
      </div>
    </div>
  </AuthGrid>
</template>

<style scoped>
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
  font-weight: var(--uc-font-weight-bold);
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