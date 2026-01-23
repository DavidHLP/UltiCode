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
import type { RouteLocationRaw } from "vue-router";
import { useRoute } from "vue-router";
import { useI18n } from "vue-i18n";
import { computed } from "vue";

const route = useRoute();
const { t } = useI18n();

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
    to: { name: "contest-home" },
  },
]);

const isActiveNav = (item: NavItem) => {
  if (!item.to) return false;
  if (typeof item.to === "string") return route.path === item.to;
  if (typeof item.to === "object") {
    if ("name" in item.to && item.to.name) return route.name === item.to.name;
    if ("path" in item.to && item.to.path) return route.path === item.to.path;
  }
  return false;
};
</script>

<template>
  <SidebarProvider class="w-full" :style="{ '--sidebar-width': '280px' }">
    <AppSidebar />
    <SidebarInset>
      <header
        class="sticky top-0 z-10 flex h-16 shrink-0 items-center gap-2 border-b bg-background px-4"
      >
        <SidebarTrigger class="-ml-1" />
        <Separator orientation="vertical" class="mr-2 h-4" />
        <NavigationMenu class="hidden md:flex">
          <NavigationMenuList>
            <NavigationMenuItem v-for="item in navItems" :key="item.label">
              <NavigationMenuLink
                v-if="item.to"
                :as-child="true"
                :active="isActiveNav(item)"
                class="min-w-[120px]"
              >
                <RouterLink
                  :to="item.to"
                  class="flex flex-col items-center justify-center gap-1 rounded-md border border-transparent px-3 py-2 text-center transition hover:border-accent hover:bg-accent/30"
                >
                  <span class="text-sm font-medium md:text-base">
                    {{ item.label }}
                  </span>
                  <span
                    v-if="item.comingSoon"
                    class="rounded-full border px-2 py-0.5 text-[10px] uppercase tracking-wide text-muted-foreground md:text-xs"
                  >
                    {{ t("common.labels.soon") }}
                  </span>
                </RouterLink>
              </NavigationMenuLink>
              <NavigationMenuLink
                v-else
                :href="item.href"
                class="flex min-w-[120px] flex-col items-center justify-center gap-1"
                target="_self"
              >
                <span class="text-sm font-medium md:text-base">
                  {{ item.label }}
                </span>
                <span
                  v-if="item.comingSoon"
                  class="rounded-full border px-2 py-0.5 text-[10px] uppercase tracking-wide text-muted-foreground md:text-xs"
                >
                  {{ t("common.labels.soon") }}
                </span>
              </NavigationMenuLink>
            </NavigationMenuItem>
          </NavigationMenuList>
          <NavigationMenuIndicator />
        </NavigationMenu>
      </header>
      <main class="flex flex-1 flex-col gap-4 p-4 lg:p-6">
        <router-view />
      </main>
    </SidebarInset>
  </SidebarProvider>
</template>
