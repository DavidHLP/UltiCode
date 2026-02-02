<script setup lang="ts">
interface Props {
  mainClass?: string
  sidebarClass?: string
}

withDefaults(defineProps<Props>(), {
  mainClass: 'lg:col-span-8 space-y-6',
  sidebarClass: 'lg:col-span-4 space-y-6',
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
    <!-- Main Content -->
    <div :class="mainClass">
      <slot name="main-content" />
    </div>

    <!-- Sidebar -->
    <aside :class="sidebarClass">
      <slot name="sidebar" />
    </aside>
  </div>
</template>
