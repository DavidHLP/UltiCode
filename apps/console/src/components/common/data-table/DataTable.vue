<script setup lang="ts" generic="T extends object">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import {
  Table,
  TableBody,
  TableHeader,
  TableCell,
  TableRow,
  TableHead,
} from "@/components/ui/table";
import { SearchX } from "lucide-vue-next";
import { useVirtualizer } from "@tanstack/vue-virtual";
import { cn } from "@/lib/utils";

export interface ColumnDef {
  key: string;
  header: string;
  class?: string;
  headerClass?: string;
}

// Interface for data with an optional id property
interface Identifiable {
  id?: string | number;
}

const props = withDefaults(
  defineProps<{
    data: T[];
    columns?: ColumnDef[];
    loading?: boolean;
    hasMore?: boolean;
    emptyLabel?: string;
    emptyDescription?: string;
    loadingLabel?: string;
    virtualized?: boolean;
    virtualizedHeight?: number;
    rowHeight?: number;
  }>(),
  {
    virtualized: false,
    virtualizedHeight: 600,
    rowHeight: 48,
  },
);

// Reference columns to avoid ESLint unused variable warning
// (columns is used in the template but Vue doesn't detect template usage)
const hasColumnDefinitions = computed(() => Boolean(props.columns));

const emit = defineEmits<{
  "load-more": [];
  "row-click": [item: T];
}>();

const scrollRef = ref<HTMLDivElement | null>(null);

// Virtual scrolling setup - only initialize when virtualized is true
const virtualizer = useVirtualizer({
  count: props.data.length,
  getScrollElement: () => scrollRef.value,
  estimateSize: () => props.rowHeight,
  overscan: 10,
});

const virtualItems = computed(() => virtualizer.value.getVirtualItems());

const handleScroll = () => {
  if (props.virtualized) return; // Skip window scroll handler when virtualized

  const buffer = 200;
  const isAtBottom =
    window.innerHeight + window.scrollY >=
    document.documentElement.offsetHeight - buffer;
  if (isAtBottom) {
    emit("load-more");
  }
};

// Handle virtualized scroll for load more
watch(
  () => props.data.length,
  () => {
    if (!props.virtualized || !props.hasMore || props.loading) return;

    const range = virtualizer.value.range;
    if (range && range.endIndex >= props.data.length - 5) {
      emit("load-more");
    }
  },
);

onMounted(() => {
  if (!props.virtualized) {
    window.addEventListener("scroll", handleScroll);
  }
});

onUnmounted(() => {
  if (!props.virtualized) {
    window.removeEventListener("scroll", handleScroll);
  }
});

// Helper function to get cell value
const getCellValue = (item: T, key: string) => {
  return (item as Record<string, unknown>)[key];
};

// Helper to safely get item at index - guaranteed to return T since virtualizer only returns valid indices
const getItemAtIndex = (index: number): T => {
  const item = props.data[index];
  // Virtualizer only provides valid indices, so this should never happen
  // but we add a fallback to satisfy TypeScript
  if (!item) {
    throw new Error(
      `Invalid index ${index} for data array of length ${props.data.length}`,
    );
  }
  return item;
};
</script>

<template>
  <div
    v-if="virtualized"
    ref="scrollRef"
    class="relative w-full overflow-auto"
    :style="{ height: `${virtualizedHeight}px` }"
  >
    <div
      :style="{
        height: `${virtualizer.getTotalSize()}px`,
        width: '100%',
        position: 'relative',
      }"
    >
      <Table>
        <TableHeader class="sticky top-0 bg-background z-10">
          <slot name="header">
            <TableRow
              v-if="hasColumnDefinitions"
              class="border-b border-border"
            >
              <TableHead
                v-for="col in columns"
                :key="col.key"
                :class="[
                  'font-extrabold text-[var(--primary)] text-xxs uppercase tracking-widest py-4 px-4 bg-[var(--surface-sunken)]/60',
                  col.headerClass,
                ]"
              >
                {{ col.header }}
              </TableHead>
            </TableRow>
          </slot>
        </TableHeader>
        <TableBody>
          <template v-if="data.length > 0">
            <template v-if="hasColumnDefinitions">
              <TableRow
                v-for="virtualRow in virtualItems"
                :key="
                  (getItemAtIndex(virtualRow.index) as Identifiable).id ||
                  virtualRow.index
                "
                class="odd:bg-[var(--surface-sunken)] even:bg-card hover:bg-[var(--accent-electric)]/5 border-b border-border cursor-pointer transition-colors"
                :style="{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '100%',
                  height: `${virtualRow.size}px`,
                  transform: `translateY(${virtualRow.start}px)`,
                }"
                @click="emit('row-click', getItemAtIndex(virtualRow.index))"
              >
                <TableCell
                  v-for="col in columns"
                  :key="col.key"
                  :class="
                    cn(
                      'py-3.5 px-4 text-xs text-[var(--solarized-base00)] dark:text-[var(--silver-400)] font-medium align-middle',
                      col.class,
                    )
                  "
                >
                  <template v-if="$slots[`cell-${col.key}`]">
                    <slot
                      :name="`cell-${col.key}`"
                      :item="getItemAtIndex(virtualRow.index)"
                    />
                  </template>
                  <template v-else>
                    {{
                      getCellValue(getItemAtIndex(virtualRow.index), col.key)
                    }}
                  </template>
                </TableCell>
              </TableRow>
            </template>
          </template>
          <TableRow v-else>
            <TableCell colspan="100%" class="p-0">
              <slot name="empty">
                <div
                  class="flex flex-col items-center justify-center py-24 border-2 border-dashed border-muted/50 rounded-none bg-muted/5 text-center px-6 m-4"
                >
                  <div
                    class="flex h-16 w-16 items-center justify-center rounded-none bg-muted/50 mb-4"
                  >
                    <SearchX class="h-8 w-8 text-muted-foreground/50" />
                  </div>
                  <p class="text-xl font-bold text-foreground">
                    {{ emptyLabel || "No results found" }}
                  </p>
                  <p class="text-sm text-muted-foreground mt-2 max-w-[300px]">
                    {{
                      emptyDescription ||
                      "Try adjusting your filters or search query."
                    }}
                  </p>
                </div>
              </slot>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
    <div v-if="hasMore" class="text-center py-4 text-muted-foreground">
      {{ loadingLabel || "Loading more..." }}
    </div>
  </div>

  <!-- Non-virtualized mode (original implementation) -->
  <div v-else class="relative w-full overflow-auto">
    <Table>
      <TableHeader>
        <slot name="header">
          <TableRow v-if="hasColumnDefinitions" class="border-b border-border">
            <TableHead
              v-for="col in columns"
              :key="col.key"
              :class="[
                'font-extrabold text-[var(--primary)] text-xxs uppercase tracking-widest py-4 px-4 bg-[var(--surface-sunken)]/60',
                col.headerClass,
              ]"
            >
              {{ col.header }}
            </TableHead>
          </TableRow>
        </slot>
      </TableHeader>

      <TableBody>
        <template v-if="data.length > 0">
          <template v-if="hasColumnDefinitions">
            <TableRow
              v-for="(item, index) in data"
              :key="(item as Identifiable).id || index"
              class="odd:bg-[var(--surface-sunken)] even:bg-card hover:bg-[var(--accent-electric)]/5 border-b border-border cursor-pointer transition-colors"
              @click="emit('row-click', item)"
            >
              <TableCell
                v-for="col in columns"
                :key="col.key"
                :class="
                  cn(
                    'py-3.5 px-4 text-xs text-[var(--solarized-base00)] dark:text-[var(--silver-400)] font-medium align-middle',
                    col.class,
                  )
                "
              >
                <template v-if="$slots[`cell-${col.key}`]">
                  <slot :name="`cell-${col.key}`" :item="item" />
                </template>
                <template v-else>
                  {{ getCellValue(item, col.key) }}
                </template>
              </TableCell>
            </TableRow>
          </template>
          <template v-else>
            <slot
              name="row"
              v-for="item in data"
              :key="(item as Identifiable).id || JSON.stringify(item)"
              :item="item"
            />
          </template>
        </template>

        <TableRow v-else>
          <TableCell colspan="100%" class="p-0">
            <slot name="empty">
              <div
                class="flex flex-col items-center justify-center py-24 border-2 border-dashed border-muted/50 rounded-none bg-muted/5 text-center px-6 m-4"
              >
                <div
                  class="flex h-16 w-16 items-center justify-center rounded-none bg-muted/50 mb-4"
                >
                  <SearchX class="h-8 w-8 text-muted-foreground/50" />
                </div>
                <p class="text-xl font-bold text-foreground">
                  {{ emptyLabel || "No results found" }}
                </p>
                <p class="text-sm text-muted-foreground mt-2 max-w-[300px]">
                  {{
                    emptyDescription ||
                    "Try adjusting your filters or search query."
                  }}
                </p>
              </div>
            </slot>
          </TableCell>
        </TableRow>
      </TableBody>
    </Table>
    <div v-if="hasMore" class="text-center py-4 text-muted-foreground">
      {{ loadingLabel || "Loading more..." }}
    </div>
  </div>
</template>
