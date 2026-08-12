<script setup lang="ts">
import AppSidebar from "@/features/sider/AppSidebar.vue";
import {
  NavigationMenu,
  NavigationMenuIndicator,
  NavigationMenuItem,
  NavigationMenuLink,
  NavigationMenuList,
} from "@/components/ui/navigation-menu";
import { Separator } from "@/components/ui/separator";
import {
  SidebarInset,
  SidebarProvider,
  SidebarTrigger,
} from "@/components/ui/sidebar";
import NotificationBadge from "@/components/notification/NotificationBadge.vue";
import SearchBar from "@/components/search/SearchBar.vue";
import type { RouteLocationRaw } from "vue-router";
import { useRoute } from "vue-router";
import { useI18n } from "vue-i18n";
import { computed, onMounted } from "vue";

const route = useRoute();
const { t } = useI18n();
const isDevelopment = import.meta.env.DEV;

onMounted(() => {
  if (isDevelopment) {
  }
});

type NavItem = {
  label: string;
  to?: RouteLocationRaw;
  href?: string;
  comingSoon?: boolean;
};

const navItems = computed<NavItem[]>(() => [
  {
    label: t("sidebar.problem.problemSet"),
    to: { name: "problemset" },
  },
  {
    label: t("sidebar.forum.platform"),
    to: { name: "forum-home" },
  },
  {
    label: t("sidebar.contest.contestSection"),
    to: { name: "contest-list" },
  },
]);

const isActiveNav = (item: NavItem) => {
  if (!item.to) return false;
  if (typeof item.to === "string") return route.path === item.to;
  if (typeof item.to === "object") {
    if ("name" in item.to && item.to.name) {
      return route.name === item.to.name;
    }
    if ("path" in item.to && item.to.path) return route.path === item.to.path;
  }
  return false;
};
</script>

<template>
  <SidebarProvider class="w-full" :style="{ '--sidebar-width': '220px' }">
    <AppSidebar />
    <SidebarInset>
      <header
        class="sticky top-0 z-10 flex h-14 shrink-0 items-center gap-4 border-b bg-background px-4"
      >
        <SidebarTrigger class="-ml-1" />
        <Separator orientation="vertical" class="h-6" />
        <NavigationMenu class="hidden md:flex h-full">
          <NavigationMenuList class="flex h-full items-stretch gap-1">
            <NavigationMenuItem
              v-for="item in navItems"
              :key="item.label"
              class="flex items-stretch"
            >
              <NavigationMenuLink
                v-if="item.to"
                :as-child="true"
                :active="isActiveNav(item)"
                class="flex items-stretch"
              >
                <RouterLink
                  :to="item.to"
                  :class="[
                    'flex items-center justify-center gap-1 rounded-none px-4 text-sm font-medium transition-all duration-200 border-b-2 h-full',
                    isActiveNav(item)
                      ? 'border-[var(--primary)] text-[var(--primary)] bg-[var(--primary)]/5'
                      : 'border-transparent text-[var(--foreground-muted)] dark:text-[var(--foreground-muted)] hover:text-[var(--primary)] hover:bg-[var(--primary)]/4',
                  ]"
                >
                  <span>{{ item.label }}</span>
                  <span
                    v-if="item.comingSoon"
                    class="rounded-full border px-1.5 py-0.5 text-2xs uppercase tracking-wide text-muted-foreground"
                  >
                    {{ t("common.labels.soon") }}
                  </span>
                </RouterLink>
              </NavigationMenuLink>
              <NavigationMenuLink
                v-else
                :href="item.href"
                class="flex items-center justify-center gap-1 px-4 border-b-2 border-transparent text-[var(--foreground-muted)] dark:text-[var(--foreground-muted)] hover:text-[var(--primary)] hover:bg-[var(--primary)]/4 transition-all duration-200 h-full"
                target="_self"
              >
                <span>{{ item.label }}</span>
                <span
                  v-if="item.comingSoon"
                  class="rounded-full border px-1.5 py-0.5 text-2xs uppercase tracking-wide text-muted-foreground"
                >
                  {{ t("common.labels.soon") }}
                </span>
              </NavigationMenuLink>
            </NavigationMenuItem>
          </NavigationMenuList>
          <NavigationMenuIndicator />
        </NavigationMenu>
        <div class="ml-auto flex items-center gap-3">
          <SearchBar />
          <NotificationBadge />
        </div>
      </header>
      <main
        class="flex min-w-0 flex-1 flex-col px-3 py-3 sm:px-4 sm:py-4 lg:px-6 lg:py-6 xl:px-8"
      >
        <div class="mx-auto w-full max-w-[1440px] min-w-0 px-2 sm:px-3 lg:px-4">
          <router-view />
        </div>
      </main>
    </SidebarInset>
  </SidebarProvider>
</template>
