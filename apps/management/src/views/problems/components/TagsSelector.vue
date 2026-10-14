<script setup lang="ts">
import { Button } from '@ulticode/design-system/ui/button'

import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Badge } from '@ulticode/design-system/ui/badge'
import { Input } from '@ulticode/design-system/ui/input'
import { X as IconX, Loader2 as IconLoader2 } from '@lucide/vue'

import { useProblemsStore } from '@/stores/admin/problems'

const props = defineProps<{
  modelValue: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

const store = useProblemsStore()
const { t } = useI18n()

const searchQuery = ref('')

const modelValueSafe = computed(() => props.modelValue || [])

const selectedTagIds = computed(() => new Set(modelValueSafe.value))

const selectedTags = computed(() => {
  return modelValueSafe.value
    .map((id) => store.allTags.find((tag) => tag.id === id))
    .filter((tag): tag is NonNullable<typeof tag> => tag !== undefined)
})

const filteredTags = computed(() => {
  if (!searchQuery.value.trim()) {
    return store.allTags
  }
  const query = searchQuery.value.toLowerCase()
  return store.allTags.filter((tag) => tag.label.toLowerCase().includes(query))
})

const showSearch = computed(() => store.allTags.length > 20)

function toggleTag(tagId: string) {
  const current = [...modelValueSafe.value]
  const index = current.indexOf(tagId)

  if (index > -1) {
    current.splice(index, 1)
  } else {
    current.push(tagId)
  }

  emit('update:modelValue', current)
}

function removeTag(tagId: string) {
  const current = modelValueSafe.value.filter((id) => id !== tagId)
  emit('update:modelValue', current)
}

function isSelected(tagId: string): boolean {
  return selectedTagIds.value.has(tagId)
}

onMounted(() => {
  if (store.allTags.length === 0 && !store.tagsLoading) {
    store.fetchAllTags()
  }
})
</script>

<template>
  <div class="space-y-4">
    <!-- Selected Tags -->
    <div class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-xs font-mono font-bold uppercase tracking-wider text-foreground">
          {{ t('problems.tagsSelector.selected') }}
        </span>
        <span v-if="selectedTags.length" class="text-xs font-mono text-muted-foreground">
          {{ selectedTags.length }} {{ t('problems.tagsSelector.selectedCount') }}
        </span>
      </div>

      <div
        v-if="selectedTags.length > 0"
        class="flex flex-wrap gap-1.5"
        data-testid="selected-tags"
      >
        <Badge
          v-for="tag in selectedTags"
          :key="tag.id"
          variant="secondary"
          class="gap-1 pr-1 px-2.5 py-0.5 h-7"
        >
          {{ tag.label }}
          <Button
            variant="ghost"
            type="button"
            class="cursor-pointer p-0.5 [&_svg]:text-destructive"
            :aria-label="t('problems.tagsSelector.removeTag', { tag: tag.label })"
            @click="removeTag(tag.id)"
          >
            <IconX class="h-3 w-3" />
          </Button>
        </Badge>
      </div>

      <p v-else class="text-xs text-muted-foreground font-mono italic">
        {{ t('problems.tagsSelector.noTagsSelected') }}
      </p>
    </div>

    <!-- Search Input -->
    <div v-if="showSearch" class="space-y-2">
      <Input
        v-model="searchQuery"
        type="text"
        :placeholder="t('problems.tagsSelector.searchPlaceholder')"
        data-testid="tag-search-input"
      />
    </div>

    <!-- Loading State -->
    <div
      v-if="store.tagsLoading"
      class="flex items-center gap-2 text-xs font-mono text-muted-foreground"
    >
      <IconLoader2 class="h-3.5 w-3.5 animate-spin" />
      {{ t('problems.tagsSelector.loading') }}
    </div>

    <!-- Available Tags -->
    <div v-else class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-xs font-mono font-bold uppercase tracking-wider text-foreground">
          {{ t('problems.tagsSelector.available') }}
        </span>
        <span class="text-xs font-mono text-muted-foreground">
          {{ filteredTags.length }} {{ t('problems.tagsSelector.totalCount') }}
        </span>
      </div>

      <div
        v-if="filteredTags.length > 0"
        class="flex flex-wrap gap-1.5"
        data-testid="available-tags"
      >
        <Button
          variant="outline"
          v-for="tag in filteredTags"
          :key="tag.id"
          type="button"
          class="cursor-pointer focus-visible:outline-none flex items-center justify-center"
          :aria-pressed="isSelected(tag.id)"
          @click="toggleTag(tag.id)"
        >
          <Badge
            :variant="isSelected(tag.id) ? 'default' : 'outline'"
            class="select-none px-2.5 py-1 h-7"
            :class="[
              isSelected(tag.id)
                ? 'border-transparent font-semibold'
                : 'bg-transparent text-muted-foreground border-border hover:bg-muted/30 hover:text-foreground',
            ]"
          >
            {{ tag.label }}
          </Badge>
        </Button>
      </div>

      <p v-else-if="searchQuery" class="text-xs text-muted-foreground font-mono italic">
        {{ t('problems.tagsSelector.noResults') }}
      </p>

      <p v-else class="text-xs text-muted-foreground font-mono italic">
        {{ t('problems.tagsSelector.noTagsAvailable') }}
      </p>
    </div>
  </div>
</template>
