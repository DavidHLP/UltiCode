<script setup lang="ts">
import AppSidebar from "@/features/sider/AppSidebar.vue";
import {
  NavigationMenu,
  NavigationMenuItem,
  NavigationMenuLink,
  NavigationMenuList,
} from "@/components/ui/navigation-menu";
import {
  SidebarInset,
  SidebarProvider,
  SidebarTrigger,
} from "@/components/ui/sidebar";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import SearchBar from "@/components/search/SearchBar.vue";
import type { RouteLocationRaw } from "vue-router";
import { useRoute } from "vue-router";
import { useI18n } from "vue-i18n";
import { computed, onMounted } from "vue";
import { cn } from "@/lib/utils";

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
  activePath: string;
  href?: string;
  comingSoon?: boolean;
};

const navItems = computed<NavItem[]>(() => [
  {
    label: t("sidebar.problem.problemSet"),
    to: { name: "problemset" },
    activePath: "/problemset",
  },
  {
    label: t("sidebar.forum.platform"),
    to: { name: "forum-home" },
    activePath: "/forum",
  },
  {
    label: t("sidebar.contest.contestSection"),
    to: { name: "contest-list" },
    activePath: "/contest",
  },
]);

const isActiveNav = (item: NavItem) => {
  return (
    route.path === item.activePath ||
    route.path.startsWith(`${item.activePath}/`)
  );
};
</script>

<template>
  <SidebarProvider class="w-full" :style="{ '--sidebar-width': '220px' }">
    <AppSidebar />
    <SidebarInset>
      <header
        class="sticky top-0 z-30 grid h-14 shrink-0 grid-cols-[1fr_auto_1fr] items-center gap-[var(--uc-layout-control-gap)] border-b border-border-subtle bg-surface-elevated px-[var(--uc-layout-panel-padding-inline)]"
      >
        <Tooltip>
          <TooltipTrigger as-child>
            <SidebarTrigger class="-ml-1 justify-self-start" />
          </TooltipTrigger>
          <TooltipContent side="bottom">
            {{ t("shortcuts.toggleSidebar") }}
          </TooltipContent>
        </Tooltip>
        <NavigationMenu class="hidden h-full justify-self-center md:flex">
          <NavigationMenuList
            class="flex h-full items-center gap-[var(--uc-layout-control-gap)]"
          >
            <NavigationMenuItem
              v-for="item in navItems"
              :key="item.label"
              class="flex items-stretch"
            >
              <NavigationMenuLink
                v-if="item.to"
                :as-child="true"
                :active="isActiveNav(item)"
                :class="cn(
                  'terminal-tab h-[var(--uc-layout-control-height)] flex-row items-center py-0',
                  isActiveNav(item) && 'border-b-2 border-primary !font-semibold',
                )"
              >
                <RouterLink :to="item.to">
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
                :class="cn(
                  'terminal-tab h-[var(--uc-layout-control-height)] flex-row items-center py-0',
                  isActiveNav(item) && 'border-b-2 border-primary !font-semibold',
                )"
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
        </NavigationMenu>
        <div class="flex items-center justify-self-end">
          <SearchBar />
        </div>
      </header>
      <main class="uc-page-main">
        <div class="uc-page-container">
          <router-view />
        </div>
      </main>
    </SidebarInset>
  </SidebarProvider>
</template>
