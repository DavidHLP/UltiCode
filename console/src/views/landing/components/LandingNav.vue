<script setup lang="ts">
/**
 * LandingNav — public top navigation for the landing page. Auth-aware:
 * guests see login/register, signed-in users get a direct platform entry.
 * Theme and language live in one settings dropdown, reusing the shared
 * switcher sub-menus.
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

const navLinks = computed(() => [
  { name: "problemset", label: t("landing.nav.problems") },
  { name: "contest-home", label: t("landing.nav.contests") },
  { name: "forum-home", label: t("landing.nav.community") },
  { name: "contest-rankings", label: t("landing.nav.rankings") },
]);

defineOptions({ name: "LandingNav" });
</script>

<template>
  <header
    class="sticky top-0 z-30 border-b border-border/40 bg-background/80 backdrop-blur-sm"
  >
    <div
      class="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6"
    >
      <RouterLink
        :to="{ name: 'landing' }"
        class="font-data text-sm font-bold uppercase tracking-[0.25em] text-foreground"
      >
        UltiCode
      </RouterLink>

      <nav
        :aria-label="t('landing.nav.primary')"
        class="hidden items-center gap-6 md:flex"
      >
        <RouterLink
          v-for="link in navLinks"
          :key="link.name"
          :to="{ name: link.name }"
          class="text-sm text-muted-foreground transition-colors hover:text-foreground"
        >
          {{ link.label }}
        </RouterLink>
      </nav>

      <div class="flex items-center gap-2">
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button
              variant="ghost"
              size="icon"
              :aria-label="t('landing.nav.settings')"
            >
              <Settings2 class="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" class="min-w-44">
            <ThemeSwitcher />
            <LanguageSwitcher />
          </DropdownMenuContent>
        </DropdownMenu>

        <template v-if="isAuthenticated">
          <Button as-child size="sm">
            <RouterLink :to="{ name: 'forum-home' }">
              {{ t("landing.nav.enter") }}
            </RouterLink>
          </Button>
        </template>
        <template v-else>
          <Button as-child variant="ghost" size="sm" class="hidden sm:inline-flex">
            <RouterLink :to="{ name: 'login' }">
              {{ t("landing.nav.login") }}
            </RouterLink>
          </Button>
          <Button as-child size="sm">
            <RouterLink :to="{ name: 'register' }">
              {{ t("landing.nav.register") }}
            </RouterLink>
          </Button>
        </template>
      </div>
    </div>
  </header>
</template>
