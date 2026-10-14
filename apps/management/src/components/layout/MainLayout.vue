<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppSidebar from './AppSidebar.vue'
import SiteHeader from './SiteHeader.vue'
import { SidebarInset, SidebarProvider } from '@ulticode/design-system/ui/sidebar'
</script>

<template>
  <SidebarProvider>
    <AppSidebar />
    <SidebarInset>
      <SiteHeader />
      <main class="uc-page-main min-h-0 overflow-auto bg-background">
        <div
          class="uc-page-container uc-page-stack flex-1"
          style="max-width: var(--page-max-width)"
        >
          <RouterView />
        </div>
      </main>
    </SidebarInset>
  </SidebarProvider>
</template>
