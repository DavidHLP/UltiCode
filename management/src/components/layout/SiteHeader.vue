<script setup lang="ts">
import { SidebarTrigger } from '@/components/ui/sidebar'

const props = defineProps<{
  title?: string
}>()
</script>

<template>
  <header
    class="flex h-(--header-height) shrink-0 items-center border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)] transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-(--header-height)"
  >
    <div class="flex w-full items-center gap-3 px-4 lg:px-6">
      <SidebarTrigger
        class="h-7 w-7 text-[var(--silver-500)] hover:text-[var(--foreground)] transition-colors"
      />
      <div class="h-4 w-px bg-[var(--silver-200)] dark:bg-[var(--silver-300)]" />
      <div class="flex items-center gap-2">
        <span class="terminal-prompt text-sm">nav</span>
        <span class="terminal-cursor" />
      </div>
      <h1
        class="text-sm font-medium tracking-tight text-[var(--foreground)] font-data uppercase tracking-wider"
      >
        {{ props.title || 'Dashboard' }}
      </h1>
    </div>
  </header>
</template>
