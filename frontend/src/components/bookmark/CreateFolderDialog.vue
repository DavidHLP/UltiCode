<script setup lang="ts">
import { ref, computed, watch } from "vue";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Loader2 } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
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
];

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
    <DialogContent class="sm:max-w-[425px] rounded-3xl">
      <DialogHeader>
        <DialogTitle class="text-2xl font-black tracking-tight">{{
          title
        }}</DialogTitle>
        <DialogDescription class="text-muted-foreground">
          {{
            isEdit
              ? t("bookmark.updateDetails")
              : t("bookmark.createDescription")
          }}
        </DialogDescription>
      </DialogHeader>

      <form @submit.prevent="handleSubmit" class="space-y-6 py-4">
        <div class="space-y-2">
          <Label
            for="name"
            class="text-[10px] font-black uppercase tracking-[0.2em] text-muted-foreground/70"
            >{{ t("bookmark.name") }}</Label
          >
          <Input
            id="name"
            v-model="name"
            :placeholder="t('bookmark.placeholders.folderName')"
            :disabled="isSubmitting"
            class="rounded-xl border-muted-foreground/20 focus:ring-primary/20 h-11"
          />
        </div>

        <div class="space-y-2">
          <Label
            for="description"
            class="text-[10px] font-black uppercase tracking-[0.2em] text-muted-foreground/70"
            >{{ t("bookmark.description") }}</Label
          >
          <Textarea
            id="description"
            v-model="description"
            :placeholder="t('bookmark.placeholders.description')"
            :disabled="isSubmitting"
            rows="3"
            class="rounded-xl border-muted-foreground/20 focus:ring-primary/20 resize-none p-3"
          />
        </div>

        <div class="space-y-4">
          <Label
            class="text-[10px] font-black uppercase tracking-[0.2em] text-muted-foreground/70"
            >{{ t("bookmark.colorAppearance") }}</Label
          >
          <div class="grid grid-cols-6 gap-3">
            <button
              v-for="color in COLORS"
              :key="color"
              type="button"
              class="h-8 w-8 rounded-full border-2 transition-all flex items-center justify-center relative group"
              :class="[
                `bg-${color}-500 border-transparent`,
                selectedColor === color
                  ? 'border-primary ring-2 ring-primary/20 scale-110'
                  : 'hover:scale-110 hover:shadow-md',
              ]"
              @click="selectedColor = color"
              :title="t(`bookmark.colors.${color}`)"
            >
              <div
                v-if="selectedColor === color"
                class="h-2 w-2 rounded-full bg-white shadow-sm"
              ></div>
            </button>
            <button
              type="button"
              class="h-8 w-8 rounded-full border-2 border-dashed transition-all bg-muted/50 flex items-center justify-center hover:bg-muted"
              :class="[
                !selectedColor
                  ? 'border-primary ring-2 ring-primary/20 scale-110'
                  : 'border-muted-foreground/30 hover:scale-110',
              ]"
              @click="selectedColor = null"
              :title="t('bookmark.noColor')"
            >
              <div class="h-4 w-0.5 bg-muted-foreground/40 rotate-45"></div>
            </button>
          </div>
        </div>

        <DialogFooter class="gap-2 sm:gap-0">
          <Button
            type="button"
            variant="ghost"
            @click="handleClose"
            class="rounded-xl font-bold"
          >
            {{ t("common.actions.cancel") }}
          </Button>
          <Button
            type="submit"
            :disabled="!name.trim() || isSubmitting"
            class="rounded-xl px-8 font-black shadow-sm"
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
