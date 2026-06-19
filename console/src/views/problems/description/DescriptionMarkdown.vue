<script setup lang="ts">
import { computed } from "vue";
import { renderMarkdown } from "@/utils/markdown";
import { sanitizeHtml } from "@/utils/sanitize";
import { toast } from "vue-sonner";
import { useI18n } from "vue-i18n";

/**
 * Problem description data model for markdown rendering.
 */
export interface ProblemDescription {
  /** Main description body */
  content: string;
  /** Example list */
  examples?: Array<{
    input: string;
    output: string;
    explanation?: string;
  }>;
  /** Constraint bullets */
  constraints?: string[];
  /** Follow-up prompt */
  followUp?: string;
}

const props = defineProps<{
  /** Problem description data */
  description: ProblemDescription;
}>();

const { t } = useI18n();

const handleCopy = (e: MouseEvent) => {
  const target = (e.target as HTMLElement).closest(".lc-copy-btn");
  if (!target) return;

  const code = (target as HTMLElement).dataset.code;
  if (code) {
    try {
      const decoded = decodeURIComponent(code);
      navigator.clipboard.writeText(decoded);
      toast.success(t("problem.messages.codeCopied"));
    } catch (err) {
      console.error("Failed to copy", err);
    }
  }
};

/**
 * Compose the complete Markdown content for the viewer.
 * Optimized: Use template literals and minimal string operations
 */
const markdownContent = computed(() => {
  const parts: string[] = [props.description.content];

  // Add examples
  if (props.description.examples?.length) {
    parts.push("\n\n");

    props.description.examples.forEach((example, index) => {
      parts.push(
        `### ${t("common.labels.example")} ${index + 1}\n`,
        `> **${t("problem.editor.input")}:** \`${example.input}\`\n>\n`,
        `> **${t("problem.editor.expectedOutput")}:** \`${example.output}\`\n`,
      );

      if (example.explanation) {
        parts.push(
          `>\n> **${t("common.labels.explanation")}:** ${example.explanation}\n`,
        );
      }

      parts.push(`\n`);
    });
  }

  // Add constraints
  if (props.description.constraints?.length) {
    parts.push(
      `\n\n### ${t("problem.detail.tags")}\n\n`,
      ...props.description.constraints.map((c) => `- ${c}\n`),
    );
  }

  // Add follow-up
  if (props.description.followUp) {
    parts.push(`

### ${t("problem.detail.hints")}

${props.description.followUp}
`);
  }

  return parts.join("");
});

const htmlContent = computed(() =>
  sanitizeHtml(renderMarkdown(markdownContent.value)),
);
</script>

<template>
  <div class="description-markdown" @click="handleCopy">
    <div class="markdown-content markdown-block" v-html="htmlContent"></div>
  </div>
</template>

<style scoped>
.description-markdown :deep(.markdown-content) {
  color: var(--solarized-base00);
}

.dark .description-markdown :deep(.markdown-content) {
  color: var(--solarized-base0);
}


.description-markdown :deep(.markdown-content h1),
.description-markdown :deep(.markdown-content h2),
.description-markdown :deep(.markdown-content h3),
.description-markdown :deep(.markdown-content h4) {
  color: var(--solarized-base02);
  font-weight: var(--uc-font-weight-bold);
  margin-top: 1.5rem;
  margin-bottom: 0.75rem;
  font-family: var(--font-sans);
}

.dark .description-markdown :deep(.markdown-content h1),
.dark .description-markdown :deep(.markdown-content h2),
.dark .description-markdown :deep(.markdown-content h3),
.dark .description-markdown :deep(.markdown-content h4) {
  color: var(--solarized-base1);
}

.description-markdown :deep(.markdown-content h1) {
  font-size: var(--uc-text-2xl);
}
.description-markdown :deep(.markdown-content h2) {
  font-size: var(--uc-text-xl);
}
.description-markdown :deep(.markdown-content h3) {
  font-size: var(--uc-text-lg);
}
.description-markdown :deep(.markdown-content h4) {
  font-size: var(--uc-text-md);
}

.description-markdown :deep(.markdown-content p) {
  margin-bottom: 1em;
}


.description-markdown :deep(.markdown-content ul),
.description-markdown :deep(.markdown-content ol) {
  padding-left: 1.25rem;
  margin-bottom: 1rem;
}

.description-markdown :deep(.markdown-content ul) {
  list-style: disc;
}

.description-markdown :deep(.markdown-content li) {
  margin-bottom: 0.35rem;
}


.description-markdown :deep(.markdown-content code) {
  font-family: var(--font-mono);
  font-size: var(--uc-type-code-size);
  line-height: var(--uc-leading-code);
  background-color: var(--silver-100);
  color: var(--solarized-base01);
  padding: 0.1rem 0.3rem;
  border-radius: 0;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid var(--border);
}

.dark .description-markdown :deep(.markdown-content code) {
  color: var(--solarized-base1);
}


.description-markdown :deep(.markdown-content pre) {
  background-color: var(--silver-100);
  border-radius: 0;
  padding: 0.75rem 1rem;
  margin: 1rem 0;
  overflow-x: auto;
  border: 1px solid var(--border);
}

.description-markdown :deep(.markdown-content pre code) {
  background-color: transparent;
  padding: 0;
  border-radius: 0;
  border: none;
  color: inherit;
  font-size: inherit;
}


.description-markdown :deep(.markdown-content blockquote) {
  border-left: 3px solid var(--solarized-blue);
  background-color: var(--silver-100);
  padding: 0.75rem 1rem;
  margin: 1rem 0;
  color: var(--solarized-base00);
  font-family: inherit;
  font-size: inherit;
  line-height: var(--uc-leading-normal);
}

.dark .description-markdown :deep(.markdown-content blockquote) {
  color: var(--solarized-base0);
}

.description-markdown :deep(.markdown-content blockquote code) {
  background-color: transparent;
  border: none;
  padding: 0;
  color: inherit;
}


.description-markdown :deep(.markdown-content strong) {
  font-weight: var(--uc-font-weight-semibold);
  color: var(--solarized-base01);
}

.dark .description-markdown :deep(.markdown-content strong) {
  color: var(--solarized-base1);
}


.description-markdown :deep(.katex) {
  font-size: var(--uc-text-md);
}

.description-markdown :deep(.katex-display) {
  margin: 0.5rem 0;
  overflow-x: auto;
  overflow-y: hidden;
}
</style>
