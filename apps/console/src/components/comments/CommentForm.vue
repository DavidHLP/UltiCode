<template>
  <form @submit.prevent="submit" class="w-full">
    <!-- Terminal-styled comment input container -->
    <div
      class="overflow-hidden rounded-md border border-border-control bg-surface-elevated shadow-xs transition-colors duration-200 focus-within:border-ring focus-within:ring-2 focus-within:ring-ring/30"
    >
      <!-- Textarea area -->
      <Textarea
        v-model="content"
        :placeholder="t('forum.comments.placeholder')"
        class="min-h-[100px] w-full resize-none border-0 bg-transparent px-4 py-3 font-mono text-sm leading-relaxed text-foreground placeholder:text-foreground-muted focus-visible:ring-0 focus-visible:ring-offset-0 disabled:cursor-not-allowed disabled:opacity-50"
      />

      <!-- Action bar -->
      <div
        class="flex items-center justify-between border-t border-border-control bg-surface-sunken px-3 py-2"
      >
        <!-- Left: Attachment buttons -->
        <div class="flex items-center gap-1">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            class="h-8 w-8 rounded-md text-foreground-muted transition-colors duration-200 hover:bg-surface-highlight hover:text-primary"
          >
            <ImageIcon class="size-4" aria-hidden="true" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            class="h-8 w-8 rounded-md text-foreground-muted transition-colors duration-200 hover:bg-surface-highlight hover:text-primary"
          >
            <Type class="size-4" aria-hidden="true" />
          </Button>
        </div>

        <!-- Right: Action buttons -->
        <div class="flex items-center gap-2">
          <Button
            v-if="onCancel"
            type="button"
            variant="ghost"
            size="sm"
            class="h-8 rounded-md px-3 font-data text-xs uppercase tracking-wider text-foreground-muted hover:bg-surface-highlight hover:text-foreground-strong"
            @click="onCancel"
          >
            {{ t("common.actions.cancel") }}
          </Button>
          <Button
            type="submit"
            variant="default"
            size="sm"
            class="h-8 rounded-md px-4 font-data text-xs uppercase tracking-wider"
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
