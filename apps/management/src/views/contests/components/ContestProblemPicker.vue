<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@ulticode/design-system/ui/dialog'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@ulticode/design-system/ui/command'
import { Button } from '@ulticode/design-system/ui/button'
import { Plus, Loader2 } from '@lucide/vue'
import SemanticBadge from '@ulticode/badge-config/SemanticBadge.vue'
import { DIFFICULTY_COLOR_MAP } from '@ulticode/badge-config'
import { useProblemsStore } from '@/stores/admin/problems'
import { useDebounceFn } from '@vueuse/core'

const props = defineProps<{
  open: boolean
  excludeIds?: (string | number)[]
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'select', problem: { id: string; title: string; difficulty: string; slug: string }): void
}>()

const problemsStore = useProblemsStore()
const { t } = useI18n()
const searchQuery = ref('')

const filteredProblems = computed(() => {
  return problemsStore.problems.filter((p) => !props.excludeIds?.includes(Number(p.id)))
})

const debouncedSearch = useDebounceFn(async (query: string) => {
  await problemsStore.fetchProblems({
    search: query,
    limit: 20,
    sortBy: 'title',
  })
}, 300)

function handleInput(e: Event) {
  const value = (e.target as HTMLInputElement).value
  searchQuery.value = value
  debouncedSearch(value)
}

function handleSelect(problem: { id: string; title: string; difficulty: string; slug: string }) {
  emit('select', problem)
}

// Initial fetch
debouncedSearch('')
</script>

<template>
  <Dialog :open="open" @update:open="$emit('update:open', $event)">
    <DialogContent class="p-0 overflow-hidden max-w-2xl">
      <DialogHeader class="px-6 pt-6 pb-2">
        <DialogTitle>
          {{ t('contests.problemPicker.title') }}
        </DialogTitle>
        <DialogDescription>
          {{ t('contests.problemPicker.description') }}
        </DialogDescription>
      </DialogHeader>

      <div class="p-4">
        <Command>
          <CommandInput
            :placeholder="t('contests.problemPicker.searchPlaceholder')"
            :value="searchQuery"
            @input="handleInput"
          />
          <CommandList class="max-h-[300px] overflow-y-auto">
            <CommandEmpty v-if="problemsStore.loading" class="py-6 flex justify-center">
              <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
            </CommandEmpty>
            <CommandEmpty v-else-if="filteredProblems.length === 0" class="py-6 text-center">
              <span class="text-sm text-muted-foreground">{{
                t('contests.problemPicker.noProblemsFound')
              }}</span>
            </CommandEmpty>
            <CommandGroup v-else>
              <div class="border-b border-border dark:border-foreground px-3 py-2 bg-muted">
                <span class="text-sm font-medium">{{ t('contests.problemPicker.problems') }}</span>
              </div>
              <CommandItem
                v-for="(problem, index) in filteredProblems"
                :key="problem.id"
                :value="problem.title"
                @select="() => handleSelect(problem)"
                class="flex items-center justify-between p-3 cursor-pointer"
              >
                <div class="flex items-center gap-3">
                  <span class="font-mono text-xs text-muted-foreground w-6">
                    {{ String(index + 1).padStart(2, '0') }}
                  </span>
                  <div class="flex flex-col gap-0.5">
                    <span class="font-medium text-sm text-foreground">{{ problem.title }}</span>
                    <span class="font-mono text-xs text-muted-foreground">{{ problem.slug }}</span>
                  </div>
                  <SemanticBadge
                    :color="DIFFICULTY_COLOR_MAP[problem.difficulty] ?? 'neutral'"
                    :label="problem.difficulty.toUpperCase()"
                    size="sm"
                    dot
                  />
                </div>
                <Button size="sm" variant="outline" class="w-6 p-0">
                  <Plus class="h-3 w-3" />
                </Button>
              </CommandItem>
            </CommandGroup>
          </CommandList>
        </Command>
      </div>
    </DialogContent>
  </Dialog>
</template>
