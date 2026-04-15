<script setup lang="ts">
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

defineProps<{
  open: boolean;
  listName: string | undefined;
  loading: boolean;
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
  (e: "confirm"): void;
}>();

const { t } = useI18n();
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{
          t("problem.problemList.detail.deleteConfirmTitle")
        }}</DialogTitle>
        <DialogDescription>
          {{
            t("problem.problemList.detail.deleteConfirmDesc", {
              name: listName,
            })
          }}
        </DialogDescription>
      </DialogHeader>
      <DialogFooter class="gap-2 sm:gap-0">
        <Button variant="outline" @click="emit('update:open', false)">{{
          t("common.actions.cancel")
        }}</Button>
        <Button
          variant="destructive"
          @click="emit('confirm')"
          :disabled="loading"
        >
          {{
            loading
              ? t("common.status.saving")
              : t("problem.problemList.actions.deleteList")
          }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
