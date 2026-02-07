<script setup lang="ts">
import { Skeleton } from "@/components/ui/skeleton";
</script>

<template>
  <div class="rounded-xl border border-border/50 bg-card shadow-sm">
    <div class="space-y-4 p-4">
      <div class="flex items-start gap-3">
        <Skeleton class="h-9 w-9 rounded-full" />
        <div class="space-y-2 flex-1">
          <Skeleton class="h-4 w-1/3" />
          <Skeleton class="h-3 w-1/4" />
        </div>
      </div>
      <div class="space-y-2">
        <Skeleton class="h-6 w-3/4" />
        <Skeleton class="h-4 w-full" />
        <Skeleton class="h-4 w-2/3" />
      </div>
    </div>
    <div class="px-4 py-3 border-t border-border/50">
      <div class="flex gap-2">
        <Skeleton class="h-8 w-16" />
        <Skeleton class="h-8 w-16" />
        <Skeleton class="h-8 w-16" />
      </div>
    </div>
  </div>
</template>
