<script setup lang="ts">
import { cn } from '../utils'
import type { SidebarUser } from '../utils'

const props = defineProps<{
  user: SidebarUser
  class?: string
}>()
</script>

<template>
  <div :class="cn('flex items-center gap-2.5 px-2 py-1.5', props.class)">
    <!-- Avatar (overridable via #avatar slot) -->
    <slot name="avatar" :user="user">
      <img
        v-if="user.avatar"
        :src="user.avatar"
        :alt="user.name"
        class="size-8 shrink-0 rounded-full object-cover"
      />
      <span
        v-else
        class="flex size-8 shrink-0 items-center justify-center rounded-full bg-[var(--accent-electric)]/15 text-sm font-medium text-[var(--accent-electric)]"
      >
        {{ user.name?.charAt(0)?.toUpperCase() }}
      </span>
    </slot>

    <!-- Identity -->
    <div class="min-w-0 flex-1">
      <div class="truncate text-sm font-medium">{{ user.name }}</div>
      <div v-if="user.email" class="truncate text-xs text-[var(--silver-500)]">
        {{ user.email }}
      </div>
    </div>

    <!-- Role badge -->
    <span
      v-if="user.role"
      class="shrink-0 rounded-full bg-[var(--silver-200)]/60 px-2 py-0.5 text-xs font-medium text-[var(--solarized-base01)] dark:text-[var(--silver-400)]"
    >
      {{ user.role }}
    </span>

    <!--
      Menu actions (DropdownMenu etc.) are provided by the caller via the
      `#menu` slot, so this shared component stays free of any app-specific
      dropdown dependency.
    -->
    <slot name="menu" :user="user" />
  </div>
</template>
