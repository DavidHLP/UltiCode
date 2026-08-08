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
  <AlertDialog :open="open" @update:open="emit('update:open', $event)">
    <AlertDialogContent class="rounded-none">
      <AlertDialogHeader>
        <AlertDialogTitle>{{
          t("personal.problemLists.dialogs.deleteList")
        }}</AlertDialogTitle>
        <AlertDialogDescription>
          {{
            t("personal.problemLists.dialogs.deleteListConfirm", {
              name: listName,
            })
          }}
        </AlertDialogDescription>
      </AlertDialogHeader>
      <AlertDialogFooter>
        <AlertDialogCancel :disabled="loading" class="rounded-full">{{
          t("common.actions.cancel")
        }}</AlertDialogCancel>
        <AlertDialogAction
          class="bg-destructive text-white hover:bg-destructive/90 rounded-full"
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
