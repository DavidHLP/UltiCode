<script setup lang="ts">
import {
  SidebarGroup,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  useSidebar,
} from "@/components/ui/sidebar";
import {
  CollapsibleTrigger,
  CollapsibleContent,
} from "@/components/ui/collapsible";
import { ChevronRight } from "lucide-vue-next";
import { Badge } from "@/components/ui/badge";
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
} from "@/shared/sidebar-menu/src";

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

const isItemActive = (item: SidebarItem): boolean => {
  // A parent with children is "active" if the current path matches it or any descendant.
  if (item.children && item.children.length > 0) {
    return item.children.some((child) => isItemActive(child));
  }
  if (!item.url) return false;
  if (isExactUrl(item.url)) return route.path === item.url;
  return route.path === item.url || route.path.startsWith(item.url + "/");
};

const getItemIconColorClass = (item: SidebarItem) => {
  if (!item.url && !item.children) return "";
  return isItemActive(item)
    ? "text-[var(--primary)]"
    : "text-[var(--foreground-muted)] group-hover:text-[var(--foreground)]";
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
      <SidebarGroupCollapsible
        v-if="section.collapsible"
        v-slot="{ open: isOpen }"
        :default-open="true"
      >
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
          <SidebarMenuSub
            v-if="state !== 'collapsed'"
            class="uc-sidebar-sub-list"
          >
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
              <span class="truncate text-xs">{{ t(item.title) }}</span>
              <Badge
                v-if="item.badge"
                :variant="item.badgeVariant || 'default'"
                class="uc-sidebar-badge ml-auto"
              >
                {{ item.badge }}
              </Badge>
            </SharedSidebarMenuSubItem>
          </SidebarMenuSub>
          <SidebarMenu v-else>
            <SidebarMenuItem v-for="item in section.items" :key="item.title">
              <SidebarMenuButton
                :tooltip="t(item.title)"
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
                  <span>{{ t(item.title) }}</span>
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
        </CollapsibleContent>
      </SidebarGroupCollapsible>

      <!-- Non-collapsible Section -->
      <template v-else>
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
              :title="t(item.title)"
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
                  <span class="truncate text-xs">{{ t(child.title) }}</span>
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
              <span class="truncate">{{ t(item.title) }}</span>
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
              :tooltip="t(item.title)"
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
                <span>{{ t(item.title) }}</span>
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
