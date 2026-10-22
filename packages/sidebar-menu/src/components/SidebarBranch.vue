<script setup lang="ts">
import type { Component } from "vue";
import { computed, ref, watch } from "vue";
import { ChevronRight, Folder } from "@lucide/vue";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@ulticode/design-system/ui/collapsible";
import {
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  SidebarMenuSub,
  useSidebar,
} from "@ulticode/design-system/ui/sidebar";

const props = withDefaults(
  defineProps<{
    title: string;
    icon?: Component;
    active?: boolean;
    defaultOpen?: boolean;
  }>(),
  { active: false, defaultOpen: true },
);
const open = ref(props.defaultOpen);
const { state, isMobile, setOpen } = useSidebar();
const collapsed = computed(
  () => state.value === "collapsed" && !isMobile.value,
);
watch(
  () => props.active,
  (active) => {
    if (active) open.value = true;
  },
);
function updateOpen(value: boolean) {
  open.value = value;
  if (collapsed.value) setOpen(true);
}
</script>

<template>
  <SidebarMenu>
    <Collapsible
      :open="!collapsed && open"
      as-child
      class="group/branch"
      @update:open="updateOpen"
    >
      <SidebarMenuItem>
        <CollapsibleTrigger as-child>
          <SidebarMenuButton
            :tooltip="title"
            :aria-label="title"
            :is-active="active"
          >
            <component
              :is="icon || Folder"
              :class="active ? 'text-inherit' : 'text-muted-foreground'"
            />
            <span>{{ title }}</span>
            <ChevronRight
              v-if="!collapsed"
              class="ml-auto transition-transform duration-200 group-data-[state=open]/branch:rotate-90"
            />
          </SidebarMenuButton>
        </CollapsibleTrigger>
        <slot name="actions" />
        <CollapsibleContent v-if="!collapsed">
          <SidebarMenuSub><slot /></SidebarMenuSub>
        </CollapsibleContent>
      </SidebarMenuItem>
    </Collapsible>
  </SidebarMenu>
</template>
