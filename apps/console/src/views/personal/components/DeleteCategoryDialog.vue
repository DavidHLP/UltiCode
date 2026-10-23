<script setup lang="ts">
import { useI18n } from "vue-i18n";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@ulticode/design-system/ui/alert-dialog";

defineProps<{
  open: boolean;
  categoryName: string | undefined;
  loading: boolean;
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
  (e: "confirm"): void;
}>();

const { t } = useI18n();
</script>

<template>
  <AlertDialog :open="open" @update:open="emit('update:open', $event)">
    <AlertDialogContent>
      <AlertDialogHeader>
        <AlertDialogTitle>{{
          t("personal.problemLists.dialogs.deleteCategory")
        }}</AlertDialogTitle>
        <AlertDialogDescription>
          {{
            t("personal.problemLists.dialogs.deleteCategoryConfirm", {
              name: categoryName,
            })
          }}
        </AlertDialogDescription>
      </AlertDialogHeader>
      <AlertDialogFooter>
        <AlertDialogCancel :disabled="loading">{{
          t("common.actions.cancel")
        }}</AlertDialogCancel>
        <AlertDialogAction @click="emit('confirm')" :disabled="loading">
          {{
            loading ? t("common.status.saving") : t("common.actions.confirm")
          }}
        </AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>
</template>
