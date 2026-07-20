<script setup lang="ts">
/**
 * FinaleSection — the network collapses back to one clean cursor. Closing
 * CTA mirrors the hero (head-to-tail echo), then a minimal footer with real
 * project entries only.
 */
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { useAuthStore } from "@/stores/auth";
import { Button } from "@/components/ui/button";

const { t } = useI18n();
const authStore = useAuthStore();
const isAuthenticated = computed(() => authStore.isAuthenticated);

const footerLinks = computed(() => [
  { name: "problemset", label: t("landing.nav.problems") },
  { name: "contest-home", label: t("landing.nav.contests") },
  { name: "forum-home", label: t("landing.nav.community") },
  { name: "contest-rankings", label: t("landing.nav.rankings") },
]);

defineOptions({ name: "FinaleSection" });
</script>

<template>
  <section class="landing-section" aria-labelledby="landing-finale-title">
    <div class="landing-container">
      <div class="landing-block landing-reveal mx-auto max-w-2xl text-center">
        <h2 id="landing-finale-title" class="landing-title">
          {{ t("landing.finale.title") }}
        </h2>
        <p class="landing-body">{{ t("landing.finale.body") }}</p>
        <div class="mt-8 flex flex-wrap items-center justify-center gap-3">
          <Button as-child size="lg" class="landing-btn-primary">
            <RouterLink :to="{ name: 'problemset' }">
              {{ t("landing.finale.ctaPrimary") }}
            </RouterLink>
          </Button>
          <Button as-child variant="outline" size="lg" class="landing-btn-ghost">
            <RouterLink
              :to="{ name: isAuthenticated ? 'personal-profile' : 'register' }"
            >
              {{
                isAuthenticated
                  ? t("landing.finale.ctaSecondaryAuthed")
                  : t("landing.finale.ctaSecondary")
              }}
            </RouterLink>
          </Button>
        </div>
      </div>
    </div>
  </section>

  <footer class="landing-footer">
    <div
      class="mx-auto flex max-w-6xl flex-col items-center justify-between gap-4 px-4 py-8 sm:flex-row sm:px-6"
    >
      <p class="font-data text-xs uppercase tracking-[0.25em] text-muted-foreground">
        UltiCode — {{ t("landing.footer.tagline") }}
      </p>
      <nav :aria-label="t('landing.nav.footer')" class="flex items-center gap-5">
        <RouterLink
          v-for="link in footerLinks"
          :key="link.name"
          :to="{ name: link.name }"
          class="text-xs text-muted-foreground transition-colors hover:text-foreground"
        >
          {{ link.label }}
        </RouterLink>
      </nav>
    </div>
  </footer>
</template>
