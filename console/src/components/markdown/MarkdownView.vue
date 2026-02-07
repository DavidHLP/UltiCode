<script setup lang="ts">
import { renderMarkdown } from "@/utils/markdown";
import { toast } from "vue-sonner";
import { useI18n } from "vue-i18n";

import type { MarkdownViewProps } from "./type";

const props = defineProps<MarkdownViewProps>();
const { t } = useI18n();

const handleClick = (e: MouseEvent) => {
  const target = e.target as HTMLElement;

  // Handle Copy Button
  const copyBtn = target.closest(".lc-copy-btn");
  if (copyBtn) {
    const code = (copyBtn as HTMLElement).dataset.code;
    if (code) {
      try {
        const decoded = decodeURIComponent(code);
        navigator.clipboard.writeText(decoded);
        toast.success(t("common.messages.copiedToClipboard"));
      } catch (err) {
        console.error("Failed to copy", err);
      }
    }
    return;
  }

  // Handle Tab Switch
  const tabBtn = target.closest(".lc-tab-btn");
  if (tabBtn) {
    const container = tabBtn.closest(".lc-code-tabs");
    if (!container) return;

    const index = (tabBtn as HTMLElement).dataset.index;
    if (index === undefined) return;

    // Update Buttons
    container.querySelectorAll(".lc-tab-btn").forEach((btn) => {
      if ((btn as HTMLElement).dataset.index === index) {
        btn.classList.add("active");
      } else {
        btn.classList.remove("active");
      }
    });

    // Update Panels
    container.querySelectorAll(".lc-code-panel").forEach((panel) => {
      if ((panel as HTMLElement).dataset.index === index) {
        panel.classList.add("active");
      } else {
        panel.classList.remove("active");
      }
    });
  }
};
</script>

<template>
  <div class="markdown-view" @click="handleClick">
    <div
      class="prose prose-sm dark:prose-invert max-w-none w-full bg-transparent"
      v-html="renderMarkdown(props.content)"
    ></div>
  </div>
</template>

<style scoped>
/* Styles are now in @/assets/markdown.css */
</style>
