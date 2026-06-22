<script setup lang="ts">
import {
  SidebarGroup,
  SidebarGroupLabel,
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
import type { SidebarSection } from "./sidebar.data";
import { useRoute } from "vue-router";
import {
  SidebarMenuItem as SharedSidebarMenuItem,
  SidebarMenuSubItem as SharedSidebarMenuSubItem,
  SidebarGroupCollapsible,
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

const isItemActive = (url?: string) => {
  if (!url) return false;
  if (
    url === "/" ||
    url === "/forum" ||
    url === "/contest" ||
    url === "/personal" ||
    url === "/problemset"
  ) {
    return route.path === url;
  }
  return route.path.startsWith(url);
};

const getItemIconColorClass = (url?: string) => {
  if (!url) return "";
  const active = isItemActive(url);
  if (url.includes("/forum/c/interview")) {
    return active
      ? "text-[#f59e0b]"
      : "text-[var(--silver-400)] dark:text-[var(--silver-500)] group-hover:text-[#f59e0b]";
  }
  if (url.includes("/forum/c/career")) {
    return active
      ? "text-[#14b8a6]"
      : "text-[var(--silver-400)] dark:text-[var(--silver-500)] group-hover:text-[#14b8a6]";
  }
  if (url.includes("/forum/c/compensation")) {
    return active
      ? "text-[#10b981]"
      : "text-[var(--silver-400)] dark:text-[var(--silver-500)] group-hover:text-[#10b981]";
  }
  if (url.includes("/forum/c/technology")) {
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
              :is-active="isItemActive(item.url)"
              :to="item.url || '#'"
              class="flex items-center gap-2 w-full"
            >
              <component
                :is="item.icon"
                v-if="item.icon"
                :class="[
                  'h-3.5 w-3.5 shrink-0 transition-colors',
                  getItemIconColorClass(item.url),
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
                :is-active="isItemActive(item.url)"
                as-child
                :class="[
                  'group rounded-md mx-1 h-9 transition-all duration-200 border-l-4',
                  isItemActive(item.url)
                    ? 'border-[var(--accent-electric)] bg-[var(--accent-electric)]/8 text-[var(--accent-electric)] font-bold'
                    : 'border-transparent text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:bg-[var(--silver-200)]/40 hover:text-foreground',
                ]"
              >
                <router-link :to="item.url || '#'">
                  <component
                    :is="item.icon"
                    v-if="item.icon"
                    :class="[
                      'transition-colors',
                      getItemIconColorClass(item.url),
                    ]"
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
          <SharedSidebarMenuItem
            v-for="item in section.items"
            :key="item.title"
            :is-active="isItemActive(item.url)"
            :to="item.url || '#'"
          >
            <component
              :is="item.icon"
              v-if="item.icon"
              :class="[
                'h-4 w-4 shrink-0 transition-colors',
                getItemIconColorClass(item.url),
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
        </div>
        <SidebarMenu v-else class="mt-2">
          <SidebarMenuItem v-for="item in section.items" :key="item.title">
            <SidebarMenuButton
              :tooltip="t(item.title)"
              :is-active="isItemActive(item.url)"
              as-child
              :class="[
                'group rounded-md mx-1 h-9 transition-all duration-200 border-l-4',
                isItemActive(item.url)
                  ? 'border-[var(--accent-electric)] bg-[var(--accent-electric)]/8 text-[var(--accent-electric)] font-bold'
                  : 'border-transparent text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:bg-[var(--silver-200)]/40 hover:text-foreground',
              ]"
            >
              <router-link :to="item.url || '#'">
                <component
                  :is="item.icon"
                  v-if="item.icon"
                  :class="[
                    'transition-colors',
                    getItemIconColorClass(item.url),
                  ]"
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
