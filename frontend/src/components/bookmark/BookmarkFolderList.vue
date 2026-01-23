<script setup lang="ts">
import { computed } from "vue";
import {
  Bookmark,
  Folder,
  MoreHorizontal,
  Pencil,
  Trash2,
} from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import type { BookmarkFolder } from "@/types/bookmark";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  folders: BookmarkFolder[];
  selectedId?: string;
}>();

const emit = defineEmits<{
  select: [folder: BookmarkFolder];
  edit: [folder: BookmarkFolder];
  delete: [folder: BookmarkFolder];
}>();

const { t } = useI18n();

const sortedFolders = computed(() => {
  return [...props.folders].sort((a, b) => {
    if (a.isDefault && !b.isDefault) return -1;
    if (!a.isDefault && b.isDefault) return 1;
    return a.sortOrder - b.sortOrder;
  });
});
</script>

<template>
  <div class="space-y-1.5">
    <div
      v-for="folder in sortedFolders"
      :key="folder.id"
      class="group flex items-center justify-between rounded-2xl px-3 py-2.5 hover:bg-muted/50 transition-all duration-200 cursor-pointer border border-transparent"
      :class="{
        'bg-muted/80 border-muted-foreground/10 shadow-sm':
          selectedId === folder.id,
        'hover:border-muted-foreground/5': selectedId !== folder.id,
      }"
      @click="emit('select', folder)"
    >
      <div class="flex items-center gap-3.5 min-w-0">
        <div
          class="flex items-center justify-center h-10 w-10 rounded-xl shrink-0 border transition-colors"
          :class="[
            folder.color
              ? `bg-${folder.color}-500/10 border-${folder.color}-500/20 text-${folder.color}-600`
              : 'bg-muted-foreground/10 border-muted-foreground/10 text-muted-foreground',
          ]"
        >
          <component
            :is="folder.isDefault ? Bookmark : Folder"
            class="h-5 w-5 flex-shrink-0"
          />
        </div>
        <div class="min-w-0">
          <p class="text-sm font-black tracking-tight truncate">
            {{ folder.isDefault ? t("bookmark.defaultFolder") : folder.name }}
          </p>
          <p
            class="text-[10px] font-bold uppercase tracking-widest text-muted-foreground/60 flex items-center gap-1"
          >
            {{ folder.itemCount }} {{ t("bookmark.items") }}
          </p>
        </div>
      </div>

      <DropdownMenu v-if="!folder.isDefault">
        <DropdownMenuTrigger as-child>
          <Button
            variant="ghost"
            size="icon"
            class="h-8 w-8 rounded-full opacity-0 group-hover:opacity-100 transition-all hover:bg-background/80"
            @click.stop
          >
            <MoreHorizontal class="h-4 w-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" class="rounded-xl">
          <DropdownMenuItem @select="emit('edit', folder)" class="gap-2">
            <Pencil class="h-4 w-4" />
            {{ t("bookmark.actions.edit") }}
          </DropdownMenuItem>
          <DropdownMenuItem
            class="text-destructive focus:text-destructive gap-2"
            @select="emit('delete', folder)"
          >
            <Trash2 class="h-4 w-4" />
            {{ t("bookmark.actions.delete") }}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>

    <div
      v-if="folders.length === 0"
      class="py-12 text-center flex flex-col items-center justify-center"
    >
      <div class="p-4 rounded-3xl bg-muted/50 mb-3">
        <Folder class="h-8 w-8 text-muted-foreground/30" />
      </div>
      <p class="text-sm font-bold text-muted-foreground/70 tracking-tight">
        {{ t("bookmark.noCollections") }}
      </p>
    </div>
  </div>
</template>
