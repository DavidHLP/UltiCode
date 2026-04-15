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
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";

defineProps<{
  open: boolean;
  loading: boolean;
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
  (
    e: "submit",
    data: { name: string; description: string; isPublic: boolean },
  ): void;
}>();

const { t } = useI18n();

const form = ref({
  name: "",
  description: "",
  isPublic: false,
});

function handleSubmit() {
  emit("submit", { ...form.value });
}

function handleOpenChange(value: boolean) {
  emit("update:open", value);
  if (!value) {
    form.value = { name: "", description: "", isPublic: false };
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="handleOpenChange">
    <DialogContent class="sm:max-w-[425px] rounded-none">
      <DialogHeader>
        <DialogTitle class="text-2xl font-black tracking-tight">{{
          t("personal.problemLists.dialogs.createList")
        }}</DialogTitle>
        <DialogDescription>
          {{ t("personal.problemLists.dialogs.createListDesc") }}
        </DialogDescription>
      </DialogHeader>
      <div class="space-y-6 py-4">
        <div class="space-y-2">
          <Label
            for="create-name"
            class="text-xs font-bold uppercase tracking-widest text-muted-foreground"
            >{{ t("personal.problemLists.dialogs.listName") }}</Label
          >
          <Input
            id="create-name"
            v-model="form.name"
            :placeholder="
              t('personal.problemLists.dialogs.listNamePlaceholder')
            "
            class="h-11 rounded-none"
          />
        </div>
        <div class="space-y-2">
          <Label
            for="create-description"
            class="text-xs font-bold uppercase tracking-widest text-muted-foreground"
            >{{ t("personal.problemLists.dialogs.description") }}</Label
          >
          <Textarea
            id="create-description"
            v-model="form.description"
            :placeholder="
              t('personal.problemLists.dialogs.descriptionPlaceholder')
            "
            class="min-h-[100px] resize-none rounded-none"
          />
        </div>
        <div
          class="flex items-center justify-between p-4 rounded-none bg-muted/30 border"
        >
          <div class="space-y-0.5">
            <Label for="create-public" class="text-sm font-bold">{{
              t("personal.problemLists.dialogs.publicList")
            }}</Label>
            <p class="text-xs text-muted-foreground">
              {{ t("personal.problemLists.dialogs.publicListDesc") }}
            </p>
          </div>
          <Switch id="create-public" v-model:checked="form.isPublic" />
        </div>
      </div>
      <DialogFooter>
        <Button
          variant="outline"
          @click="handleOpenChange(false)"
          class="rounded-full"
          >{{ t("common.actions.cancel") }}</Button
        >
        <Button
          @click="handleSubmit"
          :disabled="loading || !form.name.trim()"
          class="rounded-full px-8"
        >
          {{
            loading
              ? t("personal.problemLists.dialogs.creating")
              : t("personal.problemLists.dialogs.createButton")
          }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
