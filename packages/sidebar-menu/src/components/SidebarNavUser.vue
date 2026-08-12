<script setup lang="ts">
import { ref } from 'vue'
import { cn } from '../utils'
import type { SidebarUser } from '../utils'

const props = defineProps<{
  user: SidebarUser
  class?: string
}>()

// Avatar image load failure (404 / broken src) falls back to the initials
// span so the row never shows a broken-image icon.
const avatarFailed = ref(false)
</script>

<template>
  <div :class="cn('flex items-center gap-2.5 px-2 py-1.5', props.class)">
    <!-- Avatar (overridable via #avatar slot) -->
    <slot name="avatar" :user="user">
      <img
        v-if="user.avatar && !avatarFailed"
        :src="user.avatar"
        :alt="user.name"
        class="size-8 shrink-0 rounded-full object-cover"
        @error="avatarFailed = true"
      />
      <span
        v-else
        class="flex size-8 shrink-0 items-center justify-center rounded-full border border-[var(--primary)] bg-surface-highlight text-sm font-medium text-foreground-strong"
      >
        {{ user.name?.charAt(0)?.toUpperCase() }}
      </span>
    </slot>

    <!-- Identity -->
    <div class="min-w-0 flex-1">
      <div class="truncate text-sm font-medium">{{ user.name }}</div>
      <div v-if="user.email" class="truncate text-xs text-foreground">
        {{ user.email }}
      </div>
    </div>

    <!-- Role badge -->
    <span
      v-if="user.role"
      class="shrink-0 rounded-full bg-surface-highlight border border-border-control px-2 py-0.5 text-xs font-medium text-foreground-strong"
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
