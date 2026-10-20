<script setup lang="ts">
import {
  SidebarGroup,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  useSidebar,
} from "@ulticode/design-system/ui/sidebar";
import {
  CollapsibleTrigger,
  CollapsibleContent,
} from "@ulticode/design-system/ui/collapsible";
import { ChevronRight } from "@lucide/vue";
import { Badge } from "@ulticode/design-system/ui/badge";
import { useI18n } from "vue-i18n";
import { computed } from "vue";
import { useAuthStore } from "@/stores/auth";
import type { SidebarItem, SidebarSection } from "./sidebar.data";
import { useRoute } from "vue-router";
import {
  SidebarMenuItem as SharedSidebarMenuItem,
  SidebarMenuSubItem as SharedSidebarMenuSubItem,
  SidebarGroupCollapsible,
  SidebarParentItem,
} from "@ulticode/sidebar-menu";

const { t } = useI18n();
const authStore = useAuthStore();
const route = useRoute();
const { state } = useSidebar();

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
  // A parent with children is "active" if the current path matches it or any descendant.
  if (item.children && item.children.length > 0) {
    return item.children.some((child) => isItemActive(child));
  }
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
  if (isExactUrl(item.url)) return route.path === item.url;
  return route.path === item.url || route.path.startsWith(item.url + "/");
};

const getItemIconColorClass = (item: SidebarItem) => {
  if (!item.url && !item.children) return "";
  return isItemActive(item)
    ? "text-inherit"
    : "text-muted-foreground group-hover:text-foreground";
};
</script>

<template>
  <div class="uc-sidebar-nav">
    <SidebarGroup
      v-for="section in visibleSections"
      :key="section.name"
      class="uc-sidebar-section"
    >
      <!-- Collapsible Section -->
      <div v-if="section.collapsible" v-show="state !== 'collapsed'">
        <SidebarGroupCollapsible v-slot="{ open: isOpen }" :default-open="true">
          <CollapsibleTrigger class="uc-sidebar-section-trigger">
            <span>{{ t(section.name) }}</span>
            <ChevronRight
              :class="[
                'uc-sidebar-section-chevron ml-auto',
                isOpen ? 'rotate-90' : '',
              ]"
            />
          </CollapsibleTrigger>
          <CollapsibleContent>
            <SidebarMenuSub class="uc-sidebar-sub-list">
              <SharedSidebarMenuSubItem
                v-for="item in section.items"
                :key="item.title"
                :is-active="isItemActive(item)"
                :to="item.url || '#'"
                class="flex items-center gap-2"
              >
                <component
                  :is="item.icon"
                  v-if="item.icon"
                  :class="[
                    'h-3.5 w-3.5 shrink-0 transition-colors',
                    getItemIconColorClass(item),
                  ]"
                />
                <span class="truncate text-xs">{{ itemLabel(item) }}</span>
                <Badge
                  v-if="item.badge"
                  :variant="item.badgeVariant || 'default'"
                  class="uc-sidebar-badge ml-auto"
                >
                  {{ item.badge }}
                </Badge>
              </SharedSidebarMenuSubItem>
            </SidebarMenuSub>
          </CollapsibleContent>
        </SidebarGroupCollapsible>
      </div>

      <!-- Non-collapsible Section -->
      <template v-if="!section.collapsible || state === 'collapsed'">
        <div v-if="state !== 'collapsed'" class="uc-sidebar-section-items">
          <template v-for="item in section.items" :key="item.title">
            <!-- Item with children: shared SidebarParentItem (link + collapsible children).
                 NOTE: console sidebar.data is currently flat (no item has
                 children), so this branch is dormant — it activates once
                 children are added. defaultOpen only seeds state at mount;
                 route-driven auto-expand would need SidebarGroupCollapsible
                 (conditional :open forwarding) instead. -->
            <SidebarParentItem
              v-if="item.children && item.children.length > 0"
              :title="itemLabel(item)"
              :url="item.url"
              :icon="item.icon"
              :icon-class="getItemIconColorClass(item)"
              :active="isItemActive(item)"
              :default-open="isItemActive(item)"
            >
              <div class="uc-sidebar-child-list">
                <SharedSidebarMenuSubItem
                  v-for="child in item.children"
                  :key="child.title"
                  :is-active="isItemActive(child)"
                  :to="child.url || '#'"
                  class="flex items-center gap-2"
                >
                  <component
                    :is="child.icon"
                    v-if="child.icon"
                    :class="[
                      'h-3.5 w-3.5 shrink-0 transition-colors',
                      getItemIconColorClass(child),
                    ]"
                  />
                  <span class="truncate text-xs">{{ itemLabel(child) }}</span>
                </SharedSidebarMenuSubItem>
              </div>
            </SidebarParentItem>
            <!-- Plain item -->
            <SharedSidebarMenuItem
              v-else
              :is-active="isItemActive(item)"
              :to="item.url || '#'"
            >
              <component
                :is="item.icon"
                v-if="item.icon"
                :class="[
                  'h-4 w-4 shrink-0 transition-colors',
                  getItemIconColorClass(item),
                ]"
              />
              <span class="truncate">{{ itemLabel(item) }}</span>
              <Badge
                v-if="item.badge"
                :variant="item.badgeVariant || 'default'"
                class="uc-sidebar-badge ml-auto"
              >
                {{ item.badge }}
              </Badge>
            </SharedSidebarMenuItem>
          </template>
        </div>
        <SidebarMenu v-else class="mt-2">
          <SidebarMenuItem v-for="item in section.items" :key="item.title">
            <SidebarMenuButton
              :tooltip="itemLabel(item)"
              :is-active="isItemActive(item)"
              as-child
              class="uc-sidebar-item group"
            >
              <router-link :to="item.url || '#'">
                <component
                  :is="item.icon"
                  v-if="item.icon"
                  :class="['transition-colors', getItemIconColorClass(item)]"
                />
                <span>{{ itemLabel(item) }}</span>
                <Badge
                  v-if="item.badge"
                  :variant="item.badgeVariant || 'default'"
                  class="uc-sidebar-badge ml-auto"
                >
                  {{ item.badge }}
                </Badge>
              </router-link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </template>
    </SidebarGroup>
  </div>
</template>
