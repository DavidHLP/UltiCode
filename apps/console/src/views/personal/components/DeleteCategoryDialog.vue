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
} from "@/components/ui/alert-dialog";

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
    <AlertDialogContent class="rounded-none">
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
        <AlertDialogCancel :disabled="loading" class="rounded-full">{{
          t("common.actions.cancel")
        }}</AlertDialogCancel>
        <AlertDialogAction
          class="bg-status-error-surface text-foreground-strong border border-destructive hover:bg-status-error-surface/80 rounded-full"
          @click="emit('confirm')"
          :disabled="loading"
        >
          {{
            loading ? t("common.status.saving") : t("common.actions.confirm")
          }}
        </AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>
</template>
