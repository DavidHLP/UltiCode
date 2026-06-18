<script setup lang="ts">
import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { ChevronRight } from "lucide-vue-next";
import { Badge } from "@/components/ui/badge";
import { useI18n } from "vue-i18n";
import { computed } from "vue";
import { useAuthStore } from "@/stores/auth";
import type { SidebarSection } from "./sidebar.data";
import { useRoute } from "vue-router";

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
      <Collapsible
        v-if="section.collapsible"
        :default-open="true"
        class="group/collapsible"
      >
        <SidebarGroupLabel
          as-child
          class="group/label w-full text-2xs font-bold tracking-widest text-[var(--solarized-base01)]/80 dark:text-[var(--silver-500)] hover:bg-transparent hover:text-[var(--accent-electric)] transition-colors select-none cursor-pointer"
        >
          <CollapsibleTrigger class="flex items-center w-full py-1">
            <span>{{ t(section.name).toUpperCase() }}</span>
            <ChevronRight
              class="ml-auto h-3 w-3 text-[var(--solarized-base01)]/80 dark:text-[var(--silver-500)] transition-transform group-data-[state=open]/collapsible:rotate-90"
            />
          </CollapsibleTrigger>
        </SidebarGroupLabel>
        <CollapsibleContent>
          <div
            v-if="state !== 'collapsed'"
            class="flex flex-col gap-0.5 px-1 py-0.5"
          >
            <router-link
              v-for="item in section.items"
              :key="item.title"
              :to="item.url || '#'"
              :class="[
                'group flex items-center gap-2.5 px-3 py-1.5 transition-all duration-200 select-none text-xxs font-medium rounded-none h-8.5 mx-1',
                isItemActive(item.url)
                  ? 'bg-[var(--accent-electric)]/10 text-[var(--accent-electric)] font-bold'
                  : 'text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:bg-[var(--accent-electric)]/4 hover:text-[var(--accent-electric)] hover:translate-x-0.5',
              ]"
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
            </router-link>
          </div>
          <SidebarMenu v-else>
            <SidebarMenuItem v-for="item in section.items" :key="item.title">
              <SidebarMenuButton
                :tooltip="t(item.title)"
                :is-active="isItemActive(item.url)"
                as-child
                :class="[
                  'group rounded-none mx-1',
                  isItemActive(item.url)
                    ? 'bg-[var(--accent-electric)]/10 text-[var(--accent-electric)] font-bold'
                    : 'text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:text-[var(--accent-electric)] hover:bg-[var(--accent-electric)]/4',
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
      </Collapsible>

      <!-- Non-collapsible Section -->
      <template v-else>
        <div
          v-if="state !== 'collapsed'"
          class="flex flex-col gap-0.5 px-1 py-0.5"
        >
          <router-link
            v-for="item in section.items"
            :key="item.title"
            :to="item.url || '#'"
            :class="[
              'group flex items-center gap-2.5 px-3 py-1.5 transition-all duration-200 select-none text-xxs font-medium rounded-none h-8.5 mx-1',
              isItemActive(item.url)
                ? 'bg-[var(--accent-electric)]/10 text-[var(--accent-electric)] font-bold'
                : 'text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:bg-[var(--accent-electric)]/4 hover:text-[var(--accent-electric)] hover:translate-x-0.5',
            ]"
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
          </router-link>
        </div>
        <SidebarMenu v-else class="mt-2">
          <SidebarMenuItem v-for="item in section.items" :key="item.title">
            <SidebarMenuButton
              :tooltip="t(item.title)"
              :is-active="isItemActive(item.url)"
              as-child
              :class="[
                'group rounded-none mx-1',
                isItemActive(item.url)
                  ? 'bg-[var(--accent-electric)]/10 text-[var(--accent-electric)] font-bold'
                  : 'text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:text-[var(--accent-electric)] hover:bg-[var(--accent-electric)]/4',
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
