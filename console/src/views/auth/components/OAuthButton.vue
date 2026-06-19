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
    >
      <path
        d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
        fill="#4285F4"
      />
      <path
        d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
        fill="#34A853"
      />
      <path
        d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
        fill="#FBBC05"
      />
      <path
        d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
        fill="#EA4335"
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
  gap: 0.5rem;
  padding: 0 0.75rem;
  font-family: var(--uc-font-code);
  font-weight: var(--uc-font-weight-bold);
  font-size: var(--uc-text-sm);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--foreground);
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 0;
  cursor: pointer;
  box-shadow: 2px 2px 0px 0px var(--border);
  transition: all var(--transition-fast);
}

.oauth-button:hover {
  background: var(--surface-sunken);
  border-color: var(--accent-electric);
  box-shadow: 3px 3px 0px 0px var(--accent-electric);
  transform: translate(-1px, -1px);
}

.oauth-button:active {
  transform: translate(1px, 1px);
  box-shadow: 1px 1px 0px 0px var(--border);
}

.oauth-button__icon {
  width: 1.25rem;
  height: 1.25rem;
}

.oauth-button__text {
  display: flex;
  align-items: center;
}
</style>
