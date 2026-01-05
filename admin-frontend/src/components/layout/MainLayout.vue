<script setup lang="ts">
import { RouterView, useRoute } from 'vue-router'
import { computed } from 'vue'
import AppSidebar from './AppSidebar.vue'
import SiteHeader from './SiteHeader.vue'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
import { Toaster } from '@/components/ui/sonner'

const route = useRoute()

// Get page title from route meta or use a default
const pageTitle = computed(() => {
  return (route.meta?.title as string) || 'Dashboard'
})
</script>

<template>
  <SidebarProvider>
    <AppSidebar />
    <SidebarInset>
      <SiteHeader :title="pageTitle" />
      <main class="flex flex-1 flex-col gap-4 p-4 lg:gap-6 lg:p-6">
        <RouterView />
      </main>
    </SidebarInset>
    <Toaster />
  </SidebarProvider>
</template>
