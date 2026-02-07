<script setup lang="ts" generic="T extends object">
import { computed, onMounted, onUnmounted } from "vue";
import {
  Table,
  TableBody,
  TableHeader,
  TableCell,
  TableRow,
  TableHead,
} from "@/components/ui/table";
import { SearchX } from "lucide-vue-next";

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

const props = defineProps<{
  data: T[];
  columns?: ColumnDef[];
  loading?: boolean;
  hasMore?: boolean;
  emptyLabel?: string;
  emptyDescription?: string;
  loadingLabel?: string;
}>();

// Reference columns to avoid ESLint unused variable warning
// (columns is used in the template but Vue doesn't detect template usage)
const hasColumnDefinitions = computed(() => Boolean(props.columns));

const emit = defineEmits<{
  "load-more": [];
  "row-click": [item: T];
}>();

const handleScroll = () => {
  const buffer = 200;
  const isAtBottom =
    window.innerHeight + window.scrollY >=
    document.documentElement.offsetHeight - buffer;
  if (isAtBottom) {
    emit("load-more");
  }
};

onMounted(() => {
  window.addEventListener("scroll", handleScroll);
});

onUnmounted(() => {
  window.removeEventListener("scroll", handleScroll);
});

// Helper function to get cell value
const getCellValue = (item: T, key: string) => {
  return (item as Record<string, unknown>)[key];
};
</script>

<template>
  <div class="relative w-full overflow-auto">
    <Table>
      <TableHeader>
        <slot name="header">
          <TableRow v-if="hasColumnDefinitions">
            <TableHead
              v-for="col in columns"
              :key="col.key"
              :class="col.headerClass"
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
              class="odd:bg-muted/30 even:bg-background hover:bg-muted/50 cursor-pointer transition-colors"
              @click="emit('row-click', item)"
            >
              <TableCell
                v-for="col in columns"
                :key="col.key"
                :class="col.class"
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
                class="flex flex-col items-center justify-center py-24 border-2 border-dashed border-muted/50 rounded-2xl bg-muted/5 text-center px-6 m-4"
              >
                <div
                  class="flex h-16 w-16 items-center justify-center rounded-2xl bg-muted/50 mb-4"
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
