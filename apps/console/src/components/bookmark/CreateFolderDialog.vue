<script setup lang="ts">
import { ref, computed, watch } from "vue";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@ulticode/design-system/ui/dialog";
import { Button } from "@ulticode/design-system/ui/button";
import { Input } from "@ulticode/design-system/ui/input";
import { Textarea } from "@ulticode/design-system/ui/textarea";
import { Label } from "@ulticode/design-system/ui/label";
import { Loader2 } from "@lucide/vue";
import { useI18n } from "vue-i18n";
import colors from "tailwindcss/colors";
import type {
  BookmarkFolder,
  CreateFolderInput,
  UpdateFolderInput,
} from "@/types/bookmark";

const props = defineProps<{
  open: boolean;
  folder?: BookmarkFolder | null;
}>();

const emit = defineEmits<{
  "update:open": [value: boolean];
  create: [data: CreateFolderInput];
  update: [id: string, data: UpdateFolderInput];
}>();

const { t } = useI18n();

const name = ref("");
const description = ref("");
const selectedColor = ref<string | null>(null);
const isSubmitting = ref(false);

const COLORS = [
  "slate",
  "red",
  "orange",
  "amber",
  "yellow",
  "lime",
  "green",
  "emerald",
  "teal",
  "cyan",
  "sky",
  "blue",
  "indigo",
  "violet",
  "purple",
  "fuchsia",
  "pink",
  "rose",
] as const;

const isEdit = computed(() => !!props.folder);
const title = computed(() =>
  isEdit.value ? t("bookmark.editFolder") : t("bookmark.createFolder"),
);

watch(
  () => props.open,
  (open) => {
    if (open && props.folder) {
      name.value = props.folder.name;
      description.value = props.folder.description ?? "";
      selectedColor.value = props.folder.color;
    } else if (open) {
      name.value = "";
      description.value = "";
      selectedColor.value = null;
    }
  },
);

async function handleSubmit() {
  if (!name.value.trim()) return;

  isSubmitting.value = true;
  try {
    if (isEdit.value && props.folder) {
      emit("update", props.folder.id, {
        name: name.value.trim(),
        description: description.value.trim() || undefined,
        color: selectedColor.value || undefined,
      });
    } else {
      emit("create", {
        name: name.value.trim(),
        description: description.value.trim() || undefined,
        color: selectedColor.value || undefined,
      });
    }
    emit("update:open", false);
  } finally {
    isSubmitting.value = false;
  }
}

function handleClose() {
  emit("update:open", false);
}
</script>

<template>
  <Dialog :open="open" @update:open="$emit('update:open', $event)">
    <DialogContent class="sm:max-w-[425px]">
      <DialogHeader>
        <DialogTitle>{{ title }}</DialogTitle>
        <DialogDescription>
          {{
            isEdit
              ? t("bookmark.updateDetails")
              : t("bookmark.createDescription")
          }}
        </DialogDescription>
      </DialogHeader>

      <form @submit.prevent="handleSubmit" class="space-y-6 py-4">
        <div class="space-y-2">
          <Label for="name">{{ t("bookmark.name") }}</Label>
          <Input
            id="name"
            v-model="name"
            :placeholder="t('bookmark.placeholders.folderName')"
            :disabled="isSubmitting"
          />
        </div>

        <div class="space-y-2">
          <Label for="description">{{ t("bookmark.description") }}</Label>
          <Textarea
            id="description"
            v-model="description"
            :placeholder="t('bookmark.placeholders.description')"
            :disabled="isSubmitting"
            rows="3"
            class="resize-none p-3"
          />
        </div>

        <div class="space-y-4">
          <Label>{{ t("bookmark.colorAppearance") }}</Label>
          <div class="grid grid-cols-6 gap-3">
            <Button
              variant="outline"
              v-for="color in COLORS"
              :key="color"
              type="button"
              class="w-8 flex items-center justify-center relative group"
              :class="[
                'border-transparent',
                selectedColor === color
                  ? 'border-primary ring-2 ring-primary/20 scale-110'
                  : 'hover:scale-110',
              ]"
              :style="{ backgroundColor: colors[color][500] }"
              :aria-pressed="selectedColor === color"
              @click="selectedColor = color"
              :title="t(`bookmark.colors.${color}`)"
            >
              <div
                v-if="selectedColor === color"
                class="h-2 w-2 rounded-full bg-primary-foreground shadow-sm"
              ></div>
            </Button>
            <Button
              variant="outline"
              type="button"
              class="w-8 flex items-center justify-center"
              :class="[
                !selectedColor
                  ? 'border-primary ring-2 ring-primary/20 scale-110'
                  : 'border-muted-foreground/30 hover:scale-110',
              ]"
              @click="selectedColor = null"
              :aria-pressed="!selectedColor"
              :title="t('bookmark.noColor')"
            >
              <div class="h-4 w-0.5 bg-muted-foreground/40 rotate-45"></div>
            </Button>
          </div>
        </div>

        <DialogFooter class="gap-2 sm:gap-0">
          <Button type="button" variant="ghost" @click="handleClose">
            {{ t("common.actions.cancel") }}
          </Button>
          <Button
            type="submit"
            :disabled="!name.trim() || isSubmitting"
            class="px-8"
          >
            <Loader2 v-if="isSubmitting" class="mr-2 h-4 w-4 animate-spin" />
            {{
              isEdit ? t("bookmark.saveChanges") : t("bookmark.createFolder")
            }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
