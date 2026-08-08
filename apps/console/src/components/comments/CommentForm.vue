<template>
  <form @submit.prevent="submit" class="w-full">
    <!-- Terminal-styled comment input container -->
    <div
      class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)] focus-within:border-[var(--accent-electric)] focus-within:ring-1 focus-within:ring-[var(--accent-electric)]/30 transition-all duration-200"
    >
      <!-- Textarea area -->
      <Textarea
        v-model="content"
        :placeholder="t('forum.comments.placeholder')"
        class="min-h-[100px] w-full resize-none border-0 bg-transparent px-4 py-3 text-sm placeholder:text-[var(--silver-400)]/60 focus-visible:ring-0 focus-visible:ring-offset-0 disabled:cursor-not-allowed disabled:opacity-50 font-mono leading-relaxed"
      />

      <!-- Action bar -->
      <div
        class="flex items-center justify-between px-3 py-2 bg-[var(--surface-sunken)] border-t border-[var(--silver-200)] dark:border-[var(--silver-300)]"
      >
        <!-- Left: Attachment buttons -->
        <div class="flex items-center gap-1">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            class="h-8 w-8 text-[var(--silver-500)] hover:text-[var(--primary)] hover:bg-[var(--primary)]/10 transition-colors duration-200"
          >
            <ImageIcon class="h-4 w-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            class="h-8 w-8 text-[var(--silver-500)] hover:text-[var(--primary)] hover:bg-[var(--primary)]/10 transition-colors duration-200"
          >
            <Type class="h-4 w-4" />
          </Button>
        </div>

        <!-- Right: Action buttons -->
        <div class="flex items-center gap-2">
          <Button
            v-if="onCancel"
            type="button"
            variant="ghost"
            size="sm"
            class="h-8 px-3 font-mono text-xs uppercase tracking-wider"
            @click="onCancel"
          >
            {{ t("common.actions.cancel") }}
          </Button>
          <Button
            type="submit"
            variant="default"
            size="sm"
            class="h-8 px-4 font-mono text-xs uppercase tracking-wider"
            :disabled="!content.trim()"
          >
            {{ t("forum.comments.submit") }}
          </Button>
        </div>
      </div>
    </div>
  </form>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Image as ImageIcon, Type } from "lucide-vue-next";
import { useI18n } from "vue-i18n";

defineOptions({
  name: "CommentForm",
});

const props = defineProps<{
  parentId?: number | string;
  initialContent?: string;
  onCancel?: () => void;
}>();

const emit = defineEmits<{
  (e: "submit", content: string, parentId?: number | string): void;
}>();

const { t } = useI18n();

const content = ref(props.initialContent || "");

const submit = () => {
  if (!content.value.trim()) return;
  emit("submit", content.value, props.parentId);
  content.value = "";
};
</script>
