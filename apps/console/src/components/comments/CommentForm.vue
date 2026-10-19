<template>
  <form @submit.prevent="submit" class="w-full min-w-0">
    <!-- Comment input container -->
    <div
      class="overflow-hidden rounded-md border border-input bg-card shadow-xs transition-colors duration-200 focus-within:border-ring focus-within:ring-2 focus-within:ring-ring/30"
    >
      <!-- Textarea area -->
      <Textarea
        v-model="content"
        :placeholder="t('forum.comments.placeholder')"
        class="min-h-[100px] w-full resize-none px-4 py-3 leading-relaxed placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50"
      />

      <!-- Action bar -->
      <div
        class="flex flex-wrap items-center justify-between gap-2 border-t border-input bg-muted px-3 py-2"
      >
        <!-- Left: Attachment buttons -->
        <div class="flex items-center gap-1">
          <Button type="button" variant="ghost" size="icon" class="w-8">
            <ImageIcon class="size-4" aria-hidden="true" />
          </Button>
          <Button type="button" variant="ghost" size="icon" class="w-8">
            <Type class="size-4" aria-hidden="true" />
          </Button>
        </div>

        <!-- Right: Action buttons -->
        <div class="ml-auto flex flex-wrap items-center justify-end gap-2">
          <Button
            v-if="onCancel"
            type="button"
            variant="ghost"
            size="sm"
            class="px-3"
            @click="onCancel"
          >
            {{ t("common.actions.cancel") }}
          </Button>
          <Button
            type="submit"
            variant="default"
            size="sm"
            class="px-4"
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
import { Textarea } from "@ulticode/design-system/ui/textarea";
import { Button } from "@ulticode/design-system/ui/button";
import { Image as ImageIcon, Type } from "@lucide/vue";
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
