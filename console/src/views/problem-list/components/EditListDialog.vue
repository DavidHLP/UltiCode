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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";

defineProps<{
  open: boolean;
  form: { name: string; description: string; isPublic: boolean };
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
  (e: "submit"): void;
}>();

const { t } = useI18n();
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="sm:max-w-[425px]">
      <DialogHeader>
        <DialogTitle>{{
          t("problem.problemList.detail.editDetails")
        }}</DialogTitle>
        <DialogDescription>
          {{ t("problem.problemList.detail.editDescription") }}
        </DialogDescription>
      </DialogHeader>
      <div class="grid gap-4 py-4">
        <div class="grid grid-cols-4 items-center gap-4">
          <Label for="name" class="text-right">{{
            t("problem.problemList.dialogs.listName")
          }}</Label>
          <Input id="name" :model-value="form.name" class="col-span-3" @update:model-value="form.name = $event" />
        </div>
        <div class="grid grid-cols-4 items-center gap-4">
          <Label for="description" class="text-right">{{
            t("problem.problemList.dialogs.description")
          }}</Label>
          <Textarea
            id="description"
            :model-value="form.description"
            class="col-span-3"
            @update:model-value="form.description = $event"
          />
        </div>
        <div class="grid grid-cols-4 items-center gap-4">
          <Label for="public" class="text-right">{{
            t("problem.problemList.dialogs.publicList")
          }}</Label>
          <div class="col-span-3 flex items-center space-x-2">
            <Switch id="public" :checked="form.isPublic" @update:checked="form.isPublic = $event" />
            <span class="text-sm text-muted-foreground">{{
              form.isPublic
                ? t("problem.problemList.detail.publicHint")
                : t("problem.problemList.detail.privateHint")
            }}</span>
          </div>
        </div>
      </div>
      <DialogFooter>
        <Button type="submit" @click="emit('submit')">{{
          t("common.actions.save")
        }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
