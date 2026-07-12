<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppSidebar from './AppSidebar.vue'
import SiteHeader from './SiteHeader.vue'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
</script>

<template>
  <SidebarProvider>
    <AppSidebar />
    <SidebarInset>
      <SiteHeader />
      <main class="flex flex-1 flex-col min-h-0 min-w-0 overflow-auto bg-[var(--background)]">
        <div
          class="flex-1 w-full mx-auto px-4 py-4 lg:px-6 lg:py-6 flex flex-col gap-4 lg:gap-6"
          style="max-width: var(--page-max-width)"
        >
          <RouterView />
        </div>
      </main>
    </SidebarInset>
  </SidebarProvider>
</template>
