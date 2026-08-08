<script setup lang="ts">
import { useI18n } from "vue-i18n";
import MarkdownView from "@/components/markdown/MarkdownView.vue";

defineProps<{
  codeMarkdown: string;
}>();

const { t } = useI18n();
</script>

<template>
  <div class="space-y-2 mt-2">
    <div class="text-xs font-medium text-muted-foreground">
      {{ t("problem.submissions.code") }}
    </div>
    <MarkdownView :content="codeMarkdown" editor-id="submission-code-preview" />
  </div>
</template>
