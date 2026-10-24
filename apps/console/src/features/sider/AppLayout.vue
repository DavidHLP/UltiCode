<script setup lang="ts">
import AppSidebar from "@/features/sider/AppSidebar.vue";
import {
  NavigationMenu,
  NavigationMenuItem,
  NavigationMenuLink,
  NavigationMenuList,
} from "@ulticode/design-system/ui/navigation-menu";
import {
  SidebarInset,
  SidebarProvider,
  SidebarTrigger,
} from "@ulticode/design-system/ui/sidebar";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@ulticode/design-system/ui/tooltip";
import { Separator } from "@ulticode/design-system/ui/separator";
import SearchBar from "@/components/search/SearchBar.vue";
import type { RouteLocationRaw } from "vue-router";
import { useRoute } from "vue-router";
import { useI18n } from "vue-i18n";
import { computed } from "vue";

const route = useRoute();
const { t } = useI18n();

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
  {
    label: t("sidebar.agent"),
    to: { name: "agent-workspace" },
    activePath: "/agent",
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
  <SidebarProvider class="uc-app-shell">
    <AppSidebar />
    <SidebarInset>
      <header class="uc-app-header">
        <div class="uc-app-header-context">
          <Tooltip>
            <TooltipTrigger as-child>
              <SidebarTrigger class="uc-header-icon -ml-1" />
            </TooltipTrigger>
            <TooltipContent side="bottom">
              {{ t("shortcuts.toggleSidebar") }}
            </TooltipContent>
          </Tooltip>
          <Separator orientation="vertical" class="mr-2 hidden !h-4 md:block" />
          <NavigationMenu class="hidden whitespace-nowrap md:flex">
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
                  class="flex-row py-0"
                >
                  <RouterLink :to="item.to">
                    <span>{{ item.label }}</span>
                    <span
                      v-if="item.comingSoon"
                      class="rounded-full border px-1.5 py-0.5 text-xs uppercase tracking-wide text-muted-foreground"
                    >
                      {{ t("common.labels.soon") }}
                    </span>
                  </RouterLink>
                </NavigationMenuLink>
                <NavigationMenuLink
                  v-else
                  :href="item.href"
                  class="flex-row py-0"
                  target="_self"
                >
                  <span>{{ item.label }}</span>
                  <span
                    v-if="item.comingSoon"
                    class="rounded-full border px-1.5 py-0.5 text-xs uppercase tracking-wide text-muted-foreground"
                  >
                    {{ t("common.labels.soon") }}
                  </span>
                </NavigationMenuLink>
              </NavigationMenuItem>
            </NavigationMenuList>
          </NavigationMenu>
        </div>
        <div class="uc-app-header-actions">
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

<style scoped>
@media (min-width: 768px) and (max-width: 1023px) {
  .uc-app-header-actions :deep(.uc-header-search) {
    width: auto;
  }
}

.uc-page-main :deep(:where([id], .markdown-view strong, .markdown-view p)) {
  scroll-margin-block-start: var(--uc-app-header-height);
}
</style>
