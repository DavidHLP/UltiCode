<script setup lang="ts">
/**
 * OAuthButton - OAuth button supporting GitHub and Google
 */
import type { HTMLAttributes } from "vue";
import { computed } from "vue";
import { cn } from "@/lib/utils";
import { Github } from "lucide-vue-next";
import { useI18n } from "vue-i18n";

const props = withDefaults(
  defineProps<{
    class?: HTMLAttributes["class"];
    provider?: "github" | "google";
  }>(),
  {
    provider: "github",
  },
);

defineOptions({
  name: "OAuthButton",
});

const { t } = useI18n();

const oauthLabel = computed(() => {
  return props.provider === "github"
    ? t("auth.login.loginWithGithub")
    : t("auth.login.loginWithGoogle");
});

function handleOAuth() {
  const baseUrl = import.meta.env.VITE_API_BASE_URL;
  if (!baseUrl) {
    // Refuse to fall back to localhost: in production this would send
    // users to a dev backend. Surface the misconfiguration immediately.
    if (import.meta.env.PROD) {
      throw new Error(
        "[OAuthButton] VITE_API_BASE_URL is not configured for production. " +
          "Set it in your deployment environment before shipping.",
      );
    }
    console.warn(
      "[OAuthButton] VITE_API_BASE_URL is not set; falling back to http://localhost:9001 for dev.",
    );
  }
  const apiBase = baseUrl ?? "http://localhost:9001";
  if (props.provider === "github") {
    window.location.href = `${apiBase}/auth/github`;
  } else if (props.provider === "google") {
    window.location.href = `${apiBase}/auth/google`;
  }
}
</script>

<template>
  <button
    type="button"
    :class="cn('oauth-button', props.class)"
    @click="handleOAuth"
  >
    <Github v-if="provider === 'github'" class="oauth-button__icon" />
    <svg
      v-else-if="provider === 'google'"
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      class="oauth-button__icon"
      aria-hidden="true"
    >
      <path
        fill="#EA4335"
        d="M12 10.2v3.84h5.52c-.24 1.44-1.74 4.2-5.52 4.2-3.3 0-6-2.76-6-6.18s2.7-6.18 6-6.18c1.92 0 3.18.81 3.9 1.5l2.64-2.52C16.86 3.06 14.64 2.04 12 2.04 6.42 2.04 1.92 6.54 1.92 12.06S6.42 22.08 12 22.08c6.9 0 9.9-4.86 9.9-9.36 0-.66-.06-1.14-.18-1.62H12z"
      />
    </svg>
    <span class="oauth-button__text">
      <slot>{{ oauthLabel }}</slot>
    </span>
  </button>
</template>

<style scoped>
.oauth-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 2.375rem;
  gap: 0.75rem;
  padding: 0 1rem;
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-sm);
  font-weight: var(--uc-font-weight-medium);
  letter-spacing: var(--uc-tracking-normal);
  color: var(--foreground);
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 0;
  cursor: pointer;
  transition:
    background-color var(--transition-fast),
    border-color var(--transition-fast),
    box-shadow var(--transition-fast),
    color var(--transition-fast),
    transform var(--transition-fast);
}

.oauth-button:hover {
  background: var(--surface-sunken);
  border-color: color-mix(in oklch, var(--accent-electric) 35%, var(--border));
}

.oauth-button:focus-visible {
  outline: 2px solid var(--accent-electric);
  outline-offset: 1px;
}

.oauth-button:active {
  transform: translateY(0.5px);
}

.oauth-button__icon {
  width: 1.125rem;
  height: 1.125rem;
  flex-shrink: 0;
}

.oauth-button__text {
  display: flex;
  align-items: center;
}
</style>
