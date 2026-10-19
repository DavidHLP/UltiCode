<script setup lang="ts">
import type { HTMLAttributes } from "vue";

import { PanelLeftIcon } from "@lucide/vue";
import { cn } from "@ulticode/design-system/utils/cn";
import { Button } from "@ulticode/design-system/ui/button";
import { useSidebar } from "./utils";

const props = defineProps<{
  class?: HTMLAttributes["class"];
}>();

const { toggleSidebar } = useSidebar();
</script>

<template>
  <Button
    data-sidebar="trigger"
    data-slot="sidebar-trigger"
    variant="ghost"
    size="icon-sm"
    :class="cn('', props.class)"
    @click="toggleSidebar"
  >
    <PanelLeftIcon class="rtl:rotate-180" />
    <span class="sr-only">Toggle Sidebar</span>
  </Button>
</template>
