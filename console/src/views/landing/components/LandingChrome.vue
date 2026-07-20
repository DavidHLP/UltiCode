<script setup lang="ts">
/**
 * LandingChrome — the fixed, low-weight interface framing the exhibition:
 * brand top-left, CTA top-right, entry links bottom-left, scroll hint
 * bottom-right. Everything is plain HTML with real routes; only the CTA
 * carries visual weight.
 *
 * Desktop link hovers also emit a gather event — nearby particles drift
 * toward the matching monolith — without ever blocking the actual link.
 */
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { useAuthStore } from "@/stores/auth";
import ThemeSwitcher from "@/components/ThemeSwitcher.vue";
import LanguageSwitcher from "@/components/LanguageSwitcher.vue";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Settings2 } from "lucide-vue-next";

const { t } = useI18n();
const authStore = useAuthStore();
const isAuthenticated = computed(() => authStore.isAuthenticated);

/** Link → monolith index for the gather field (see scene/layout.ts). */
const entryLinks = computed(() => [
  { name: "problemset", label: t("landing.nav.problems"), monolith: 0 },
  { name: "contest-home", label: t("landing.nav.contests"), monolith: 1 },
  { name: "forum-home", label: t("landing.nav.community"), monolith: 2 },
  { name: "contest-rankings", label: t("landing.nav.rankings"), monolith: 1 },
]);

const finePointer =
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(pointer: fine)").matches;

function emitGather(monolith: number, strength: number): void {
  if (!finePointer) return;
  window.dispatchEvent(
    new CustomEvent("ulticode:landing-gather", {
      detail: { monolith, strength },
    }),
  );
}

defineOptions({ name: "LandingChrome" });
</script>

<template>
  <div class="landing-chrome">
    <RouterLink :to="{ name: 'landing' }" class="landing-chrome-brand">
      UltiCode
    </RouterLink>

    <div class="landing-chrome-top-right">
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <button
            type="button"
            class="landing-chrome-icon"
            :aria-label="t('landing.nav.settings')"
          >
            <Settings2 class="h-4 w-4" />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" class="min-w-44">
          <ThemeSwitcher />
          <LanguageSwitcher />
        </DropdownMenuContent>
      </DropdownMenu>

      <Button
        as-child
        variant="outline"
        size="sm"
        class="landing-chrome-cta"
      >
        <RouterLink
          :to="{ name: isAuthenticated ? 'forum-home' : 'register' }"
        >
          {{
            isAuthenticated
              ? t("landing.nav.enter")
              : t("landing.hero.ctaPrimary")
          }}
        </RouterLink>
      </Button>
    </div>

    <nav :aria-label="t('landing.nav.primary')" class="landing-chrome-links">
      <RouterLink
        v-for="link in entryLinks"
        :key="link.name"
        :to="{ name: link.name }"
        class="landing-chrome-link"
        @mouseenter="emitGather(link.monolith, 1)"
        @mouseleave="emitGather(link.monolith, 0)"
        @focus="emitGather(link.monolith, 1)"
        @blur="emitGather(link.monolith, 0)"
      >
        {{ link.label }}
      </RouterLink>
      <RouterLink
        v-if="!isAuthenticated"
        :to="{ name: 'login' }"
        class="landing-chrome-link"
      >
        {{ t("landing.nav.login") }}
      </RouterLink>
    </nav>

    <p class="landing-chrome-hint" aria-hidden="true">
      <span class="landing-chrome-hint-line" />
      {{ t("landing.chrome.scrollHint") }}
    </p>
  </div>
</template>

<style scoped>
.landing-chrome {
  position: fixed;
  inset: 0;
  z-index: 30;
  pointer-events: none;
  font-family: var(--uc-font-data);
}

.landing-chrome > * {
  pointer-events: auto;
}

.landing-chrome-brand {
  position: absolute;
  top: 1.25rem;
  left: 1.5rem;
  font-size: 0.75rem;
  font-weight: var(--uc-font-weight-medium);
  text-transform: uppercase;
  letter-spacing: 0.35em;
  color: rgba(235, 238, 242, 0.92);
  text-decoration: none;
}

.landing-chrome-top-right {
  position: absolute;
  top: 1.1rem;
  right: 1.5rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.landing-chrome-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border-radius: 9999px;
  border: 1px solid rgba(235, 238, 242, 0.2);
  color: rgba(235, 238, 242, 0.7);
  background: transparent;
  cursor: pointer;
  transition: border-color 200ms ease, color 200ms ease;
}

.landing-chrome-icon:hover,
.landing-chrome-icon:focus-visible {
  border-color: rgba(235, 238, 242, 0.55);
  color: rgba(235, 238, 242, 0.95);
}

.landing-chrome-cta {
  border-radius: 9999px;
  border-color: rgba(235, 238, 242, 0.35);
  background: transparent;
  color: rgba(235, 238, 242, 0.92);
  font-family: var(--uc-font-data);
  font-size: 0.6875rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.landing-chrome-cta:hover {
  background: rgba(235, 238, 242, 0.92);
  color: #0a0a0a;
  border-color: rgba(235, 238, 242, 0.92);
}

.landing-chrome-links {
  position: absolute;
  bottom: 1.5rem;
  left: 1.5rem;
  display: flex;
  align-items: center;
  gap: 1.25rem;
}

.landing-chrome-link {
  font-size: 0.6875rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgba(235, 238, 242, 0.45);
  text-decoration: none;
  transition: color 200ms ease;
}

.landing-chrome-link:hover,
.landing-chrome-link:focus-visible {
  color: rgba(235, 238, 242, 0.95);
}

.landing-chrome-hint {
  position: absolute;
  bottom: 1.5rem;
  right: 1.5rem;
  display: flex;
  align-items: center;
  gap: 0.625rem;
  font-size: 0.625rem;
  letter-spacing: 0.25em;
  text-transform: uppercase;
  color: rgba(235, 238, 242, 0.4);
  margin: 0;
}

.landing-chrome-hint-line {
  display: inline-block;
  width: 1px;
  height: 1.75rem;
  background: linear-gradient(
    to bottom,
    transparent,
    rgba(235, 238, 242, 0.6)
  );
  animation: landing-hint-drift 2.4s ease-in-out infinite;
}

@keyframes landing-hint-drift {
  0%,
  100% {
    transform: translateY(-3px);
    opacity: 0.4;
  }
  50% {
    transform: translateY(3px);
    opacity: 1;
  }
}

@media (max-width: 640px) {
  .landing-chrome-links {
    gap: 0.875rem;
  }

  .landing-chrome-hint {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .landing-chrome-hint-line {
    animation: none;
  }
}
</style>
