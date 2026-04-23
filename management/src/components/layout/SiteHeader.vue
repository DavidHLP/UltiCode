<script setup lang="ts">
import LanguageSwitcher from "@/components/LanguageSwitcher.vue";
</script>

<template>
  <header
    class="flex h-(--header-height) shrink-0 items-center border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)] transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-(--header-height)"
  >
    <div class="flex w-full items-center px-4 lg:px-6 gap-2">
      <LanguageSwitcher />
    </div>
  </header>
</template>
