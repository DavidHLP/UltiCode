<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppSidebar from '@/template/dashboard/AppSidebar.vue'
import SiteHeader from '@/template/dashboard/SiteHeader.vue'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
</script>

<template>
  <SidebarProvider>
    <AppSidebar />
    <SidebarInset>
      <SiteHeader />
      <main class="flex flex-1 flex-col gap-4 p-4 lg:gap-6 lg:p-6">
        <RouterView />
      </main>
    </SidebarInset>
  </SidebarProvider>
</template>
