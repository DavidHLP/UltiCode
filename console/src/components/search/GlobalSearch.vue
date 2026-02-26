<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed } from "vue";
import { useSearch } from "@/composables/useSearch";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Search,
  Code,
  User,
  MessageSquare,
  FileText,
  ArrowRight,
} from "lucide-vue-next";
import { cn } from "@/lib/utils";

const props = defineProps<{
  open?: boolean;
}>();

const emit = defineEmits<{
  "update:open": [value: boolean];
}>();

const searchHook = useSearch({ debounceMs: 300 });

const inputRef = ref<HTMLInputElement | null>(null);

// Computed for template access
const query = computed(() => searchHook.query.value);
const results = computed(() => searchHook.results.value ?? []);
const loading = computed(() => searchHook.loading.value);
const total = computed(() => searchHook.total.value);
const selectedIndex = computed(() => searchHook.selectedIndex.value);
const isOpen = computed({
  get: () => searchHook.isOpen.value,
  set: (val) => {
    searchHook.isOpen.value = val;
  },
});

// Sync isOpen with open prop
watch(
  () => props.open,
  (val) => {
    if (val !== undefined) {
      searchHook.isOpen.value = val;
      if (val) {
        setTimeout(() => inputRef.value?.focus(), 100);
      }
    }
  },
  { immediate: true },
);

watch(isOpen, (val) => {
  emit("update:open", val ?? false);
  if (!val) {
    searchHook.clear();
  }
});

function handleKeydown(e: KeyboardEvent) {
  // Open on Cmd+K / Ctrl+K
  if ((e.metaKey || e.ctrlKey) && e.key === "k") {
    e.preventDefault();
    isOpen.value = !isOpen.value;
    if (isOpen.value) {
      setTimeout(() => inputRef.value?.focus(), 100);
    }
    return;
  }

  // Only handle other keys when open
  if (!isOpen.value) return;

  switch (e.key) {
    case "ArrowDown":
      e.preventDefault();
      searchHook.selectNext();
      break;
    case "ArrowUp":
      e.preventDefault();
      searchHook.selectPrev();
      break;
    case "Enter":
      e.preventDefault();
      searchHook.selectCurrent();
      break;
    case "Escape":
      searchHook.close();
      break;
  }
}

function handleInputChange(e: Event) {
  const target = e.target as HTMLInputElement;
  searchHook.search(target.value);
}

function getTypeIcon(type: string) {
  switch (type) {
    case "problems":
      return Code;
    case "users":
      return User;
    case "posts":
      return MessageSquare;
    case "solutions":
      return FileText;
    default:
      return Search;
  }
}

function getTypeLabel(type: string) {
  switch (type) {
    case "problems":
      return "Problem";
    case "users":
      return "User";
    case "posts":
      return "Post";
    case "solutions":
      return "Solution";
    default:
      return "Result";
  }
}

function getTypeColor(type: string): string {
  switch (type) {
    case "problems":
      return "bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400";
    case "users":
      return "bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400";
    case "posts":
      return "bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400";
    case "solutions":
      return "bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400";
    default:
      return "bg-gray-100 text-gray-600 dark:bg-gray-900/30 dark:text-gray-400";
  }
}

onMounted(() => {
  window.addEventListener("keydown", handleKeydown);
});

onUnmounted(() => {
  window.removeEventListener("keydown", handleKeydown);
});
</script>

<template>
  <Dialog v-model:open="isOpen">
    <DialogContent class="max-w-2xl overflow-hidden p-0">
      <!-- Search Input -->
      <div class="flex items-center border-b px-4">
        <Search class="h-5 w-5 shrink-0 text-muted-foreground" />
        <Input
          ref="inputRef"
          :model-value="query"
          type="text"
          placeholder="Search problems, users, posts..."
          class="border-0 focus-visible:ring-0"
          @input="handleInputChange"
        />
        <kbd
          class="hidden rounded border bg-muted px-2 py-0.5 text-xs text-muted-foreground sm:inline-block"
        >
          ESC
        </kbd>
      </div>

      <!-- Results -->
      <div class="max-h-96 overflow-y-auto">
        <!-- Loading state -->
        <div v-if="loading" class="p-4">
          <Skeleton v-for="i in 3" :key="i" class="mb-2 h-14 rounded" />
        </div>

        <!-- Results list -->
        <div v-else-if="results?.length > 0" class="py-2">
          <RouterLink
            v-for="(result, index) in results ?? []"
            :key="result.id"
            :to="result.url"
            :class="
              cn(
                'flex items-center gap-3 px-4 py-3 transition-colors',
                selectedIndex === index ? 'bg-accent' : 'hover:bg-muted/50',
              )
            "
            @click="searchHook.close()"
            @mouseenter="searchHook.selectedIndex.value = index"
          >
            <!-- Type icon -->
            <div
              :class="
                cn(
                  'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg',
                  getTypeColor(result.type),
                )
              "
            >
              <component :is="getTypeIcon(result.type)" class="h-5 w-5" />
            </div>

            <!-- Content -->
            <div class="flex-1 overflow-hidden">
              <div class="flex items-center gap-2">
                <span class="truncate font-medium">{{ result.title }}</span>
                <span
                  class="shrink-0 rounded-full bg-muted px-2 py-0.5 text-xs"
                >
                  {{ getTypeLabel(result.type) }}
                </span>
              </div>
              <p
                v-if="result.description"
                class="truncate text-sm text-muted-foreground"
              >
                {{ result.description }}
              </p>
            </div>

            <!-- Arrow -->
            <ArrowRight
              :class="
                cn(
                  'h-4 w-4 shrink-0 text-muted-foreground transition-opacity',
                  selectedIndex === index ? 'opacity-100' : 'opacity-0',
                )
              "
            />
          </RouterLink>
        </div>

        <!-- Empty state -->
        <div
          v-else-if="query && !loading"
          class="flex flex-col items-center justify-center py-12 text-center"
        >
          <Search class="h-12 w-12 text-muted-foreground/50" />
          <p class="mt-4 text-sm text-muted-foreground">
            No results found for "{{ query }}"
          </p>
        </div>

        <!-- Initial state -->
        <div
          v-else-if="!query"
          class="flex flex-col items-center justify-center py-12 text-center"
        >
          <Search class="h-12 w-12 text-muted-foreground/50" />
          <p class="mt-4 text-sm text-muted-foreground">
            Start typing to search...
          </p>
          <p class="mt-1 text-xs text-muted-foreground">
            <kbd class="rounded border bg-muted px-1.5 py-0.5">Cmd</kbd> +
            <kbd class="rounded border bg-muted px-1.5 py-0.5">K</kbd>
            to open search
          </p>
        </div>
      </div>

      <!-- Footer -->
      <div
        v-if="results?.length > 0"
        class="flex items-center justify-between border-t px-4 py-2 text-xs text-muted-foreground"
      >
        <span>{{ total }} results</span>
        <div class="flex items-center gap-4">
          <span class="flex items-center gap-1">
            <kbd class="rounded border bg-muted px-1.5 py-0.5">↑</kbd>
            <kbd class="rounded border bg-muted px-1.5 py-0.5">↓</kbd>
            to navigate
          </span>
          <span class="flex items-center gap-1">
            <kbd class="rounded border bg-muted px-1.5 py-0.5">Enter</kbd>
            to select
          </span>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
