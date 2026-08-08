<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
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
import { Label } from "@/components/ui/label";

defineProps<{
  open: boolean;
  loading: boolean;
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
  (e: "submit", data: { name: string }): void;
}>();

const { t } = useI18n();

const form = ref({ name: "" });

function handleSubmit() {
  emit("submit", { ...form.value });
}

function handleOpenChange(value: boolean) {
  emit("update:open", value);
  if (!value) {
    form.value = { name: "" };
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="handleOpenChange">
    <DialogContent class="sm:max-w-md rounded-none">
      <DialogHeader>
        <DialogTitle class="text-2xl font-black tracking-tight">{{
          t("personal.problemLists.dialogs.newCategory")
        }}</DialogTitle>
        <DialogDescription>
          {{ t("personal.problemLists.dialogs.newCategoryDesc") }}
        </DialogDescription>
      </DialogHeader>
      <div class="space-y-4 py-4">
        <div class="space-y-2">
          <Label
            for="category-name"
            class="text-xs font-bold uppercase tracking-widest text-muted-foreground"
            >{{ t("personal.problemLists.dialogs.categoryName") }}</Label
          >
          <Input
            id="category-name"
            v-model="form.name"
            :placeholder="
              t('personal.problemLists.dialogs.categoryNamePlaceholder')
            "
            class="h-11 rounded-none"
            @keydown.enter="handleSubmit"
          />
        </div>
      </div>
      <DialogFooter>
        <Button
          variant="outline"
          @click="handleOpenChange(false)"
          :disabled="loading"
          class="rounded-full"
        >
          {{ t("common.actions.cancel") }}
        </Button>
        <Button
          @click="handleSubmit"
          :disabled="loading || !form.name.trim()"
          class="rounded-full px-8"
        >
          {{
            loading
              ? t("personal.problemLists.dialogs.creating")
              : t("personal.problemLists.dialogs.createCategory")
          }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
