<script setup lang="ts">
import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@ulticode/design-system/ui/dialog";
import { Button } from "@ulticode/design-system/ui/button";
import { Input } from "@ulticode/design-system/ui/input";
import { Label } from "@ulticode/design-system/ui/label";

const props = defineProps<{
  open: boolean;
  loading: boolean;
  categoryName: string;
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
  (e: "submit", name: string): void;
}>();

const { t } = useI18n();

const form = ref({ name: "" });

watch(
  () => props.open,
  (value) => {
    if (value) {
      form.value = { name: props.categoryName };
    }
  },
);

function handleSubmit() {
  emit("submit", form.value.name);
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>{{
          t("personal.problemLists.dialogs.renameCategory")
        }}</DialogTitle>
      </DialogHeader>
      <div class="space-y-4 py-4">
        <div class="space-y-2">
          <Label for="edit-category-name">{{
            t("personal.problemLists.dialogs.newName")
          }}</Label>
          <Input
            id="edit-category-name"
            v-model="form.name"
            @keydown.enter="handleSubmit"
          />
        </div>
      </div>
      <DialogFooter>
        <Button
          variant="outline"
          @click="emit('update:open', false)"
          :disabled="loading"
        >
          {{ t("common.actions.cancel") }}
        </Button>
        <Button
          @click="handleSubmit"
          :disabled="loading || !form.name.trim()"
          class="px-8"
        >
          {{ loading ? t("common.status.saving") : t("common.actions.save") }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
