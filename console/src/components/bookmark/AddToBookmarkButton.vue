<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { Bookmark, BookmarkCheck, FolderPlus, Loader2 } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuCheckboxItem,
  DropdownMenuSeparator,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import { useBookmarkStore } from "@/stores/bookmark";
import {
  addBookmark,
  removeBookmarkByTarget,
  getBookmarkFolders,
} from "@/api/bookmark";
import type { BookmarkType } from "@/types/bookmark";
import { toast } from "vue-sonner";
import { isAuthenticated } from "@/utils/auth";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  targetType: BookmarkType;
  targetId: string;
  variant?: "default" | "ghost" | "outline";
  size?: "default" | "sm" | "icon";
}>();

const emit = defineEmits<{
  change: [isFavorite: boolean, folders: string[]];
  "create-folder": [];
}>();
const store = useBookmarkStore();
const { t } = useI18n();

const itemFolders = ref<string[]>([]);
const isLoading = ref(false);
const isOpen = ref(false);
const isAuthed = ref(false);

const isFavorited = computed(() => {
  const defaultId = store.defaultFolder?.id;
  return defaultId ? itemFolders.value.includes(defaultId) : false;
});

const isBookmarked = computed(() => itemFolders.value.length > 0);

async function loadData() {
  if (!isOpen.value) return;

  isAuthed.value = isAuthenticated();
  if (!isAuthed.value) {
    itemFolders.value = [];
    isLoading.value = false;
    return;
  }

  isLoading.value = true;
  try {
    await store.loadFolders();
    itemFolders.value = await getBookmarkFolders(
      props.targetType,
      props.targetId,
    );
  } catch (error) {
    console.error("Failed to load bookmark folders:", error);
  } finally {
    isLoading.value = false;
  }
}

async function toggleFolder(folderId: string) {
  if (!isAuthenticated()) {
    toast.error(t("bookmark.toasts.loginRequired"));
    return;
  }

  const isInFolder = itemFolders.value.includes(folderId);

  try {
    if (isInFolder) {
      await removeBookmarkByTarget(folderId, props.targetType, props.targetId);
      itemFolders.value = itemFolders.value.filter((id) => id !== folderId);
      store.updateItemCount(folderId, -1);
    } else {
      await addBookmark(folderId, {
        targetId: props.targetId,
        targetType: props.targetType,
      });
      itemFolders.value.push(folderId);
      store.updateItemCount(folderId, 1);
    }

    emit("change", isBookmarked.value, itemFolders.value);
  } catch (error) {
    console.error("Failed to toggle bookmark:", error);
    toast.error(t("bookmark.toasts.updateFailed"));
  }
}

function handleOpenChange(open: boolean) {
  isOpen.value = open;
  if (open) {
    loadData();
  }
}

watch(
  () => props.targetId,
  () => {
    itemFolders.value = [];
  },
);
</script>

<template>
  <DropdownMenu @update:open="handleOpenChange">
    <DropdownMenuTrigger as-child>
      <Button
        :variant="variant ?? 'ghost'"
        :size="size ?? 'icon'"
        class="relative rounded-full transition-all"
        :class="{
          'bg-primary/10 text-primary hover:bg-primary/20': isBookmarked,
        }"
      >
        <component
          :is="isBookmarked ? BookmarkCheck : Bookmark"
          class="h-5 w-5"
        />
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent class="w-64 rounded-2xl p-2" align="end">
      <template v-if="isLoading">
        <div class="flex items-center justify-center py-8">
          <Loader2 class="h-6 w-6 animate-spin text-primary/60" />
        </div>
      </template>
      <template v-else-if="!isAuthed">
        <div class="px-4 py-6 text-center">
          <Bookmark class="h-8 w-8 text-muted-foreground/30 mx-auto mb-2" />
          <p
            class="text-xs font-bold text-muted-foreground uppercase tracking-widest"
          >
            {{ t("bookmark.pleaseLogIn") }}
          </p>
          <p class="text-[10px] text-muted-foreground mt-1">
            {{ t("bookmark.signInToSave") }}
          </p>
        </div>
      </template>
      <template v-else>
        <div class="px-2 py-1.5 mb-1">
          <h4
            class="text-[10px] font-black uppercase tracking-[0.2em] text-muted-foreground/70"
          >
            {{ t("bookmark.saveToCollection") }}
          </h4>
        </div>

        <!-- Default Folder -->
        <DropdownMenuCheckboxItem
          v-if="store.defaultFolder"
          :checked="isFavorited"
          @select.prevent="toggleFolder(store.defaultFolder.id)"
          class="rounded-xl py-2.5 gap-3"
        >
          <div
            class="flex items-center justify-center h-8 w-8 rounded-lg shrink-0 border"
            :class="[
              store.defaultFolder.color
                ? `bg-${store.defaultFolder.color}-500/10 border-${store.defaultFolder.color}-500/20 text-${store.defaultFolder.color}-600`
                : 'bg-muted-foreground/10 border-muted-foreground/10 text-muted-foreground',
            ]"
          >
            <Bookmark class="h-4 w-4" />
          </div>
          <div class="flex flex-col min-w-0">
            <span class="text-sm font-bold truncate">{{
              store.defaultFolder.isDefault
                ? t("bookmark.defaultFolder")
                : store.defaultFolder.name
            }}</span>
            <span class="text-[10px] font-bold text-muted-foreground/60"
              >{{ store.defaultFolder.itemCount }}
              {{ t("bookmark.items") }}</span
            >
          </div>
        </DropdownMenuCheckboxItem>

        <DropdownMenuSeparator
          v-if="store.customFolders.length > 0"
          class="my-1 mx-2"
        />

        <!-- Custom Folders -->
        <DropdownMenuCheckboxItem
          v-for="folder in store.customFolders"
          :key="folder.id"
          :checked="itemFolders.includes(folder.id)"
          @select.prevent="toggleFolder(folder.id)"
          class="rounded-xl py-2.5 gap-3"
        >
          <div
            class="flex items-center justify-center h-8 w-8 rounded-lg shrink-0 border"
            :class="[
              folder.color
                ? `bg-${folder.color}-500/10 border-${folder.color}-500/20 text-${folder.color}-600`
                : 'bg-muted-foreground/10 border-muted-foreground/10 text-muted-foreground',
            ]"
          >
            <Folder class="h-4 w-4" />
          </div>
          <div class="flex flex-col min-w-0">
            <span class="text-sm font-bold truncate">{{ folder.name }}</span>
            <span class="text-[10px] font-bold text-muted-foreground/60"
              >{{ folder.itemCount }} {{ t("bookmark.items") }}</span
            >
          </div>
        </DropdownMenuCheckboxItem>

        <DropdownMenuSeparator class="my-1 mx-2" />

        <DropdownMenuItem
          class="cursor-pointer rounded-xl py-2.5 gap-3 text-primary focus:text-primary"
          @select="emit('create-folder')"
        >
          <div
            class="flex items-center justify-center h-8 w-8 rounded-lg shrink-0 bg-primary/10 border border-primary/20"
          >
            <FolderPlus class="h-4 w-4" />
          </div>
          <span class="text-sm font-black">{{
            t("bookmark.createFolder")
          }}</span>
        </DropdownMenuItem>
      </template>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
