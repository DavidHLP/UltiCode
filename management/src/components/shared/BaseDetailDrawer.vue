<script setup lang="ts" generic="T">
import type { Slot } from 'vue'
import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
} from '@/components/ui/drawer'
import { ScrollArea } from '@/components/ui/scroll-area'

withDefaults(
  defineProps<{
    open: boolean
    loading?: boolean
    entity?: T | null
    title?: string
    description?: string
    loadingText?: string
    notFoundText?: string
    width?: string
  }>(),
  {
    loading: false,
    entity: null,
    title: 'Details',
    description: 'View comprehensive information.',
    loadingText: 'Loading...',
    notFoundText: 'Not found',
    width: 'w-[400px] sm:w-[540px]',
  },
)

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
}>()

defineSlots<{
  headerActions?: () => Slot
  content(props: { entity: NonNullable<T> }): Slot
}>()
</script>

<template>
  <Drawer :open="open" @update:open="emit('update:open', $event)" direction="right">
    <DrawerContent class="h-full" :class="width">
      <DrawerHeader class="border-b px-6 py-4">
        <div class="flex items-center justify-between">
          <div>
            <DrawerTitle>{{ title }}</DrawerTitle>
            <DrawerDescription>{{ description }}</DrawerDescription>
          </div>
          <slot name="headerActions" />
        </div>
      </DrawerHeader>

      <!-- Loading State -->
      <div v-if="loading" class="flex h-full items-center justify-center p-8">
        <div class="flex flex-col items-center gap-2">
          <div class="h-8 w-8 animate-spin border-4 border-primary border-t-transparent"></div>
          <p class="text-sm text-muted-foreground">{{ loadingText }}</p>
        </div>
      </div>

      <!-- Not Found State -->
      <div v-else-if="!entity" class="flex h-full items-center justify-center p-8">
        <p class="text-muted-foreground">{{ notFoundText }}</p>
      </div>

      <!-- Content -->
      <ScrollArea v-else class="flex-1">
        <div class="flex flex-col gap-6 p-6">
          <slot name="content" :entity="entity" />
        </div>
      </ScrollArea>
    </DrawerContent>
  </Drawer>
</template>
