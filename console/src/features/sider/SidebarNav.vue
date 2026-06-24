<script setup lang="ts">
import {
  SidebarGroup,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  useSidebar,
} from "@/components/ui/sidebar";
import { CollapsibleTrigger, CollapsibleContent } from "@/components/ui/collapsible";
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
  const active = isItemActive(item);
  const probeUrl = item.url || item.children?.[0]?.url || "";
  if (probeUrl.includes("/forum/c/interview")) {
    return active
      ? "text-[#f59e0b]"
      : "text-[var(--silver-400)] dark:text-[var(--silver-500)] group-hover:text-[#f59e0b]";
  }
  if (probeUrl.includes("/forum/c/career")) {
    return active
      ? "text-[#14b8a6]"
      : "text-[var(--silver-400)] dark:text-[var(--silver-500)] group-hover:text-[#14b8a6]";
  }
  if (probeUrl.includes("/forum/c/compensation")) {
    return active
      ? "text-[#10b981]"
      : "text-[var(--silver-400)] dark:text-[var(--silver-500)] group-hover:text-[#10b981]";
  }
  if (probeUrl.includes("/forum/c/technology")) {
    return active
      ? "text-[#06b6d4]"
      : "text-[var(--silver-400)] dark:text-[var(--silver-500)] group-hover:text-[#06b6d4]";
  }
  if (active) return "text-[var(--accent-electric)]";
  return "text-[var(--silver-400)] dark:text-[var(--silver-500)] group-hover:text-[var(--accent-electric)]";
};
</script>

<template>
  <div class="flex flex-col gap-3 py-1">
    <SidebarGroup
      v-for="section in visibleSections"
      :key="section.name"
      class="py-0"
    >
      <!-- Collapsible Section -->
      <SidebarGroupCollapsible
        v-if="section.collapsible"
        v-slot="{ open: isOpen }"
        :default-open="true"
      >
        <CollapsibleTrigger
          class="flex w-full items-center text-2xs font-bold tracking-widest text-[var(--solarized-base01)]/80 dark:text-[var(--silver-500)] hover:text-[var(--accent-electric)] transition-colors select-none py-1 px-2 outline-hidden"
        >
          <span>{{ t(section.name).toUpperCase() }}</span>
          <ChevronRight
            :class="[
              'ml-auto h-3 w-3 text-[var(--solarized-base01)]/80 dark:text-[var(--silver-500)] transition-transform',
              isOpen ? 'rotate-90' : '',
            ]"
          />
        </CollapsibleTrigger>
        <CollapsibleContent>
          <SidebarMenuSub
            v-if="state !== 'collapsed'"
            class="mx-3.5 border-[var(--silver-200)] dark:border-[var(--silver-300)]/50"
          >
            <SharedSidebarMenuSubItem
              v-for="item in section.items"
              :key="item.title"
              :is-active="isItemActive(item)"
              :to="item.url || '#'"
              class="flex items-center gap-2 w-full"
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
                class="ml-auto h-5 px-1.5 text-2xs"
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
                :class="[
                  'group rounded-md mx-1 h-9 transition-all duration-200 border-l-4',
                  isItemActive(item)
                    ? 'border-[var(--accent-electric)] bg-[var(--accent-electric)]/8 text-[var(--accent-electric)] font-bold'
                    : 'border-transparent text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:bg-[var(--silver-200)]/40 hover:text-foreground',
                ]"
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
                    class="ml-auto h-5 px-1.5 text-2xs"
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
        <div
          v-if="state !== 'collapsed'"
          class="flex flex-col gap-0.5 px-1 py-0.5"
        >
          <template v-for="item in section.items" :key="item.title">
            <!-- Item with children: shared SidebarParentItem (link + collapsible children) -->
            <SidebarParentItem
              v-if="item.children && item.children.length > 0"
              :title="t(item.title)"
              :url="item.url"
              :icon="item.icon"
              :icon-class="getItemIconColorClass(item)"
              :active="isItemActive(item)"
              :default-open="isItemActive(item)"
            >
              <div
                class="ml-4 mt-0.5 flex flex-col gap-0.5 border-l border-[var(--silver-200)] dark:border-[var(--silver-300)]/50 pl-2"
              >
                <SharedSidebarMenuSubItem
                  v-for="child in item.children"
                  :key="child.title"
                  :is-active="isItemActive(child)"
                  :to="child.url || '#'"
                  class="flex items-center gap-2 w-full"
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
                class="ml-auto h-5 px-1.5 text-2xs"
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
              :class="[
                'group rounded-md mx-1 h-9 transition-all duration-200 border-l-4',
                isItemActive(item)
                  ? 'border-[var(--accent-electric)] bg-[var(--accent-electric)]/8 text-[var(--accent-electric)] font-bold'
                  : 'border-transparent text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:bg-[var(--silver-200)]/40 hover:text-foreground',
              ]"
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
                  class="ml-auto h-5 px-1.5 text-2xs"
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
