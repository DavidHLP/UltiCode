<script setup lang="ts">
import { ref, onMounted } from "vue";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Save, Loader2 } from "lucide-vue-next";
import { fetchProblemNote, saveProblemNote } from "@/api/interaction";
import { toast } from "vue-sonner";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  problemId: number;
}>();

const emit = defineEmits<{
  (e: "close"): void;
}>();

const { t } = useI18n();
const content = ref("");
const isLoading = ref(true);
const isSaving = ref(false);

onMounted(async () => {
  try {
    const res = await fetchProblemNote(props.problemId);
    if (res) {
      content.value = res.content;
    }
  } catch (e) {
    console.error("Failed to fetch note", e);
  } finally {
    isLoading.value = false;
  }
});

async function handleSave() {
  isSaving.value = true;
  try {
    await saveProblemNote(props.problemId, content.value);
    toast.success(t("problem.notes.savedMsg"));
    emit("close");
  } catch (e) {
    toast.error(t("problem.notes.failedToSave"));
    console.error(e);
  } finally {
    isSaving.value = false;
  }
}
</script>

<template>
  <div class="flex flex-col h-full bg-white">
    <div class="flex items-center justify-between p-4 border-b">
      <h2 class="text-lg font-semibold">{{ t("problem.notes.title") }}</h2>
    </div>

    <div class="flex-1 p-4 overflow-y-auto">
      <div v-if="isLoading" class="flex justify-center py-10">
        <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
      <div v-else class="space-y-4">
        <p class="text-sm text-muted-foreground">
          {{ t("problem.notes.description") }}
        </p>
        <Textarea
          v-model="content"
          :placeholder="t('problem.notes.startWriting')"
          class="min-h-[400px] resize-none focus-visible:ring-1 rounded-lg"
        />
      </div>
    </div>

    <div class="p-4 border-t flex justify-end gap-2">
      <Button variant="outline" @click="emit('close')" class="rounded-full">{{
        t("common.actions.cancel")
      }}</Button>
      <Button
        :disabled="isSaving || isLoading"
        @click="handleSave"
        class="gap-2 rounded-full"
      >
        <Save v-if="!isSaving" class="h-4 w-4" />
        <Loader2 v-else class="h-4 w-4 animate-spin" />
        {{ t("problem.notes.save") }}
      </Button>
    </div>
  </div>
</template>
