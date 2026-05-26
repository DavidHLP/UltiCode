<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { IconX, IconLoader2 } from '@tabler/icons-vue'
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
        <span class="text-sm font-medium">{{ t('problems.tagsSelector.selected') }}</span>
        <span v-if="selectedTags.length" class="text-xs text-muted-foreground">
          {{ selectedTags.length }} {{ t('problems.tagsSelector.selectedCount') }}
        </span>
      </div>

      <div
        v-if="selectedTags.length > 0"
        class="flex flex-wrap gap-1.5"
        data-testid="selected-tags"
      >
        <Badge v-for="tag in selectedTags" :key="tag.id" variant="secondary" class="gap-1 pr-1.5">
          {{ tag.label }}
          <button
            type="button"
            class="hover:text-destructive text-muted-foreground hover:bg-destructive/10 rounded-full p-0.5 transition-colors"
            :aria-label="t('problems.tagsSelector.removeTag', { tag: tag.label })"
            @click="removeTag(tag.id)"
          >
            <IconX class="h-3 w-3" />
          </button>
        </Badge>
      </div>

      <p v-else class="text-sm text-muted-foreground italic">
        {{ t('problems.tagsSelector.noTagsSelected') }}
      </p>
    </div>

    <!-- Search Input -->
    <div v-if="showSearch" class="space-y-2">
      <Input
        v-model="searchQuery"
        type="text"
        :placeholder="t('problems.tagsSelector.searchPlaceholder')"
        class="text-sm"
        data-testid="tag-search-input"
      />
    </div>

    <!-- Loading State -->
    <div v-if="store.tagsLoading" class="flex items-center gap-2 text-sm text-muted-foreground">
      <IconLoader2 class="h-4 w-4 animate-spin" />
      {{ t('problems.tagsSelector.loading') }}
    </div>

    <!-- Available Tags -->
    <div v-else class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-sm font-medium">{{ t('problems.tagsSelector.available') }}</span>
        <span class="text-xs text-muted-foreground">
          {{ filteredTags.length }} {{ t('problems.tagsSelector.totalCount') }}
        </span>
      </div>

      <div
        v-if="filteredTags.length > 0"
        class="flex flex-wrap gap-1.5"
        data-testid="available-tags"
      >
        <button
          v-for="tag in filteredTags"
          :key="tag.id"
          type="button"
          class="cursor-pointer transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          :aria-pressed="isSelected(tag.id)"
          @click="toggleTag(tag.id)"
        >
          <Badge
            :variant="isSelected(tag.id) ? 'default' : 'outline'"
            class="select-none"
            :class="isSelected(tag.id) ? '' : 'hover:bg-accent hover:text-accent-foreground'"
          >
            {{ tag.label }}
          </Badge>
        </button>
      </div>

      <p v-else-if="searchQuery" class="text-sm text-muted-foreground italic">
        {{ t('problems.tagsSelector.noResults') }}
      </p>

      <p v-else class="text-sm text-muted-foreground italic">
        {{ t('problems.tagsSelector.noTagsAvailable') }}
      </p>
    </div>
  </div>
</template>
