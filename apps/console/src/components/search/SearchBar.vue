<script setup lang="ts">
import { ref } from "vue";
import { Button } from "@/components/ui/button";
import { Search } from "lucide-vue-next";
import GlobalSearch from "./GlobalSearch.vue";

const showSearch = ref(false);

function openSearch() {
  showSearch.value = true;
}
</script>

<template>
  <div>
    <Button
      variant="ghost"
      size="icon"
      class="rounded-md text-foreground-muted hover:bg-surface-highlight hover:text-foreground-strong focus-visible:ring-2 focus-visible:ring-ring"
      @click="openSearch"
    >
      <Search class="h-5 w-5" />
      <span class="sr-only">Search</span>
    </Button>

    <GlobalSearch v-model:open="showSearch" />
  </div>
</template>
