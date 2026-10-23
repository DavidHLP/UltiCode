<script setup lang="ts">
import {
  SidebarGroup,
  SidebarMenuSub,
  SidebarMenuSubItem,
  SidebarMenuSubButton,
} from "@ulticode/design-system/ui/sidebar";
import { Badge } from "@ulticode/design-system/ui/badge";
import { useI18n } from "vue-i18n";
import { computed } from "vue";
import { useAuthStore } from "@/stores/auth";
import type { SidebarItem, SidebarSection } from "./sidebar.data";
import { useRoute } from "vue-router";
import { SidebarBranch, SidebarParentItem } from "@ulticode/sidebar-menu";

const { t } = useI18n();
const authStore = useAuthStore();
const route = useRoute();

const props = defineProps<{
  sections: SidebarSection[];
}>();

const visibleSections = computed(() => {
  const isAuth = authStore.isAuthenticated;
  return props.sections
    .filter((section) => !section.requiresAuth || isAuth)
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => !item.requiresAuth || isAuth),
    }))
    .filter((section) => section.items.length > 0);
});

const EXACT_URLS = new Set<string>([
  "/",
  "/forum",
  "/contest",
  "/personal",
  "/problemset",
]);

const isExactUrl = (url?: string) => !!url && EXACT_URLS.has(url);
const itemLabel = (item: SidebarItem) => item.label ?? t(item.title);

const isItemActive = (item: SidebarItem): boolean => {
  if (!item.url) return false;
  if (item.url === "/contest/past" && route.path === "/contest/browse/past") {
    return true;
  }
  if (
    route.path === "/contest/rankings" &&
    (item.url === "/contest/global-ranking" ||
      item.url === "/contest/local-ranking")
  ) {
    const scope = route.query.scope === "local" ? "local" : "global";
    return item.url === `/contest/${scope}-ranking`;
  }
  if (item.children?.length || isExactUrl(item.url)) return route.path === item.url;
  return route.path === item.url || route.path.startsWith(item.url + "/");
};

const containsActiveItem = (item: SidebarItem): boolean =>
  isItemActive(item) || Boolean(item.children?.some(containsActiveItem));

const getItemIconColorClass = (item: SidebarItem) => {
  if (!item.url && !item.children) return "";
  return isItemActive(item)
    ? "text-inherit"
    : "text-muted-foreground group-hover:text-foreground";
};
</script>

<template>
  <div>
    <SidebarGroup v-for="section in visibleSections" :key="section.name">
      <SidebarBranch
        :title="t(section.name)"
        :icon="section.items[0]?.icon"
        :contains-active="section.items.some(containsActiveItem)"
      >
        <SidebarMenuSubItem v-for="item in section.items" :key="item.title">
          <SidebarParentItem
            v-if="item.children?.length"
            :title="itemLabel(item)"
            :url="item.url"
            :icon="item.icon"
            :active="isItemActive(item)"
            :default-open="containsActiveItem(item)"
          >
            <SidebarMenuSub>
              <SidebarMenuSubItem
                v-for="child in item.children"
                :key="child.title"
              >
                <SidebarMenuSubButton :is-active="isItemActive(child)" as-child>
                  <router-link :to="child.url || '#'">
                    <component
                      :is="child.icon"
                      v-if="child.icon"
                      class="size-4 shrink-0"
                    />
                    <span>{{ itemLabel(child) }}</span>
                  </router-link>
                </SidebarMenuSubButton>
              </SidebarMenuSubItem>
            </SidebarMenuSub>
          </SidebarParentItem>
          <SidebarMenuSubButton v-else :is-active="isItemActive(item)" as-child>
            <router-link :to="item.url || '#'">
              <component
                :is="item.icon"
                v-if="item.icon"
                :class="['size-4 shrink-0', getItemIconColorClass(item)]"
              />
              <span>{{ itemLabel(item) }}</span>
              <Badge
                v-if="item.badge"
                :variant="item.badgeVariant || 'default'"
                class="ml-auto"
                >{{ item.badge }}</Badge
              >
            </router-link>
          </SidebarMenuSubButton>
        </SidebarMenuSubItem>
      </SidebarBranch>
    </SidebarGroup>
  </div>
</template>
