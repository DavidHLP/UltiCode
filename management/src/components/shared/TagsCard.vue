<script setup lang="ts">
import { IconTag } from '@tabler/icons-vue'
import { Badge } from '@/components/ui/badge'
import { computed } from 'vue'

interface Props {
  title: string
  tags?: string[] | Array<{ id?: string; label: string }>
  count?: number
}

const props = withDefaults(defineProps<Props>(), {
  tags: () => [],
})

const normalizedTags = computed(() => {
  return props.tags.map((tag) => (typeof tag === 'string' ? tag : tag.label))
})

const hasCount = computed(() => props.count !== undefined)
</script>

<template>
  <div class="rounded-none border bg-card overflow-hidden shadow-sm">
    <div class="flex items-center gap-2 p-4 border-b bg-muted/20">
      <IconTag class="h-4 w-4 text-muted-foreground" />
      <h3 class="font-semibold text-sm">{{ title }}</h3>
      <Badge v-if="hasCount" variant="secondary" class="ml-auto text-xs">{{ count }}</Badge>
    </div>
    <div class="p-4">
      <div class="flex flex-wrap gap-1.5">
        <Badge
          v-for="(tag, index) in normalizedTags"
          :key="
            typeof tags[index] === 'object' && 'id' in (tags[index] as object)
              ? (tags[index] as { id: string }).id
              : index
          "
          variant="secondary"
          class="px-2.5 py-0.5 text-xs font-normal"
        >
          {{ tag }}
        </Badge>
      </div>
    </div>
  </div>
</template>
