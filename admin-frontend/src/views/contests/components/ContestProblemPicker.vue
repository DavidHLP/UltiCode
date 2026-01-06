<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Plus, Loader2 } from 'lucide-vue-next'
import { useProblemsStore } from '@/stores/admin/problems'
import { useDebounceFn } from '@vueuse/core'

const props = defineProps<{
  open: boolean
  excludeIds?: string[]
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'select', problem: { id: string; title: string; difficulty: string; slug: string }): void
}>()

const problemsStore = useProblemsStore()
const searchQuery = ref('')

const filteredProblems = computed(() => {
  return problemsStore.problems.filter((p) => !props.excludeIds?.includes(p.id))
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
  // Don't close automatically to allow selecting multiple?
  // Or close if single select. For now, let parent decide or keep open.
  // Actually, UI pattern usually is select -> close or list -> add button
  // Let's assume this is a picker that stays open until closed or select logic.
  // But standard CommandItem click usually implies selection.
  // We'll just emit select.
}

// Initial fetch
debouncedSearch('')
</script>

<template>
  <Dialog :open="open" @update:open="$emit('update:open', $event)">
    <DialogContent class="p-0 overflow-hidden max-w-2xl">
      <DialogHeader class="px-6 pt-6 pb-2">
        <DialogTitle>Select Problem</DialogTitle>
        <DialogDescription>
          Search and select a problem to add to the contest.
        </DialogDescription>
      </DialogHeader>
      <div class="px-4 pb-4">
        <Command class="border rounded-md">
          <CommandInput
            placeholder="Search problems by title or slug..."
            :value="searchQuery"
            @input="handleInput"
          />
          <CommandList class="max-h-[300px] overflow-y-auto">
            <CommandEmpty v-if="problemsStore.loading" class="py-6 flex justify-center">
              <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
            </CommandEmpty>
            <CommandEmpty
              v-else-if="filteredProblems.length === 0"
              class="py-6 text-center text-sm text-muted-foreground"
            >
              No problems found.
            </CommandEmpty>
            <CommandGroup v-else heading="Problems">
              <CommandItem
                v-for="problem in filteredProblems"
                :key="problem.id"
                :value="problem.title"
                @select="() => handleSelect(problem)"
                class="flex items-center justify-between p-2 cursor-pointer hover:bg-accent"
              >
                <div class="flex items-center gap-2">
                  <span class="font-medium">{{ problem.title }}</span>
                  <span class="text-xs text-muted-foreground">({{ problem.slug }})</span>
                  <Badge variant="outline" class="text-[10px] capitalize">
                    {{ problem.difficulty.toLowerCase() }}
                  </Badge>
                </div>
                <Button size="sm" variant="ghost" class="h-6 w-6 p-0">
                  <Plus class="h-4 w-4" />
                </Button>
              </CommandItem>
            </CommandGroup>
          </CommandList>
        </Command>
      </div>
    </DialogContent>
  </Dialog>
</template>
