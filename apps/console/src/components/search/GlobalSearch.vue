<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed } from "vue";
import { useI18n } from "vue-i18n";
import { useSearch } from "@/composables/useSearch";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Search, Code, User, MessageSquare, FileText } from "lucide-vue-next";
import { cn } from "@/lib/utils";

const { t } = useI18n();

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

onMounted(() => {
  window.addEventListener("keydown", handleKeydown);
});

onUnmounted(() => {
  window.removeEventListener("keydown", handleKeydown);
});
</script>

<template>
  <Dialog v-model:open="isOpen">
    <DialogContent class="max-w-2xl overflow-hidden p-0" hide-close>
      <!-- Search Input -->
      <div class="flex items-center border-b px-4">
        <Search class="h-5 w-5 shrink-0 text-muted-foreground" />
        <Input
          ref="inputRef"
          :model-value="query"
          type="text"
          :placeholder="t('common.search.placeholder')"
          class="border-0 focus-visible:ring-0"
          @input="handleInputChange"
        />
        <kbd
          class="hidden rounded-none border border-border-control bg-[var(--surface-sunken)] px-2 py-0.5 text-xs text-muted-foreground font-data sm:inline-block"
        >
          ESC
        </kbd>
      </div>

      <!-- Results -->
      <div class="max-h-96 overflow-y-auto">
        <!-- Loading state -->
        <div v-if="loading" class="p-4">
          <Skeleton v-for="i in 3" :key="i" class="mb-2 h-14 rounded-none" />
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
                selectedIndex === index
                  ? 'bg-accent font-bold'
                  : 'hover:bg-muted/50',
              )
            "
            @click="searchHook.close()"
            @mouseenter="searchHook.selectedIndex.value = index"
          >
            <component
              :is="getTypeIcon(result.type)"
              class="h-5 w-5 text-muted-foreground"
            />
            <div class="flex flex-col">
              <span class="text-sm font-medium">{{ result.title }}</span>
              <span
                v-if="result.description"
                class="text-xs text-muted-foreground"
              >
                {{ result.description }}
              </span>
            </div>
          </RouterLink>
        </div>

        <!-- Empty state -->
        <div
          v-else-if="query && !loading"
          class="flex flex-col items-center justify-center py-12 text-center"
        >
          <Search class="h-12 w-12 text-muted-foreground/30" />
          <p class="mt-4 text-sm text-muted-foreground">
            {{ t("common.search.noResults", { query }) }}
          </p>
        </div>

        <!-- Initial state -->
        <div
          v-else-if="!query"
          class="flex flex-col items-center justify-center py-12 text-center"
        >
          <Search class="h-12 w-12 text-muted-foreground/50" />
          <p class="mt-4 text-sm text-muted-foreground">
            {{ t("common.search.startTyping") }}
          </p>
          <p class="mt-1 text-xs text-muted-foreground">
            <kbd
              class="rounded-none border border-border-control bg-[var(--surface-sunken)] px-1.5 py-0.5 font-data"
              >Cmd</kbd
            >
            +
            <kbd
              class="rounded-none border border-border-control bg-[var(--surface-sunken)] px-1.5 py-0.5 font-data"
              >K</kbd
            >
            {{ t("common.search.openSearchTip") }}
          </p>
        </div>
      </div>

      <!-- Footer -->
      <div
        v-if="results?.length > 0"
        class="flex items-center justify-between border-t px-4 py-2 text-xs text-muted-foreground"
      >
        <span>{{ t("common.search.resultsCount", { total }) }}</span>
        <div class="flex items-center gap-4">
          <span class="flex items-center gap-1">
            <kbd
              class="rounded-none border border-border-control bg-[var(--surface-sunken)] px-1.5 py-0.5 font-data"
              >↑</kbd
            >
            <kbd
              class="rounded-none border border-border-control bg-[var(--surface-sunken)] px-1.5 py-0.5 font-data"
              >↓</kbd
            >
            {{ t("common.search.navigateTip") }}
          </span>
          <span class="flex items-center gap-1">
            <kbd
              class="rounded-none border border-border-control bg-[var(--surface-sunken)] px-1.5 py-0.5 font-data"
              >Enter</kbd
            >
            {{ t("common.search.selectTip") }}
          </span>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
