<script setup lang="ts">
import { computed } from "vue";
import { renderMarkdown } from "@/utils/markdown";
import "highlight.js/styles/atom-one-dark.css";
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

const htmlContent = computed(() => renderMarkdown(markdownContent.value));
</script>

<template>
  <div class="description-markdown" @click="handleCopy">
    <div class="markdown-content" v-html="htmlContent"></div>
  </div>
</template>

<style scoped>
.description-markdown :deep(.markdown-content) {
  font-size: 14px;
  line-height: 1.6;
  color: var(--foreground);
  font-family:
    -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif,
    "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
}

/* Headings */
.description-markdown :deep(.markdown-content h1) {
  font-size: 1.5rem;
  font-weight: 600;
  margin-bottom: 1rem;
}

.description-markdown :deep(.markdown-content h2) {
  font-size: 1.25rem;
  font-weight: 600;
  margin-top: 1.5rem;
  margin-bottom: 0.75rem;
}

.description-markdown :deep(.markdown-content h3) {
  font-size: 1rem;
  font-weight: 700;
  margin-top: 1.5rem;
  margin-bottom: 0.75rem;
}

.description-markdown :deep(.markdown-content h4) {
  font-size: 0.875rem;
  font-weight: 700;
  margin-top: 1rem;
  margin-bottom: 0.5rem;
}

.description-markdown :deep(.markdown-content p) {
  margin-bottom: 1em;
}

/* Lists */
.description-markdown :deep(.markdown-content ul),
.description-markdown :deep(.markdown-content ol) {
  padding-left: 1.25rem;
  margin-bottom: 1rem;
}

.description-markdown :deep(.markdown-content ul) {
  list-style: disc;
}

.description-markdown :deep(.markdown-content li) {
  margin-bottom: 0.25rem;
}

/* Inline Code - LeetCode Pill Style */
.description-markdown :deep(.markdown-content code) {
  font-family: "Menlo", "Monaco", "Consolas", "Courier New", monospace;
  font-size: 0.85em;
  background-color: var(--muted); /* Use theme muted bg */
  color: var(--foreground);
  padding: 0.125rem 0.375rem; /* Pill padding */
  border-radius: 0.25rem; /* Rounded corners */
  white-space: pre-wrap; /* Allow wrapping in inline code */
  word-break: break-word;
}

/* Code Blocks */
.description-markdown :deep(.markdown-content pre) {
  background-color: var(--muted);
  border-radius: 0.5rem;
  padding: 1rem;
  margin: 1rem 0;
  overflow-x: auto;
}

.description-markdown :deep(.markdown-content pre code) {
  background-color: transparent;
  padding: 0;
  border-radius: 0;
  color: inherit;
  font-size: 0.9em;
}

/* Blockquote - Matches LeetCode example blocks */
.description-markdown :deep(.markdown-content blockquote) {
  border-left: 3px solid var(--border);
  padding-left: 1rem;
  color: var(--muted-foreground);
  margin: 1rem 0;
}

/* Strong text */
.description-markdown :deep(.markdown-content strong) {
  font-weight: 600;
}

/* Katex adjustments */
.description-markdown :deep(.katex) {
  font-size: 1.1em;
}

.description-markdown :deep(.katex-display) {
  margin: 0.5rem 0;
  overflow-x: auto;
  overflow-y: hidden;
}

/* Dark mode tweaks if necessary */
.dark .description-markdown :deep(.markdown-content code) {
  background-color: rgba(255, 255, 255, 0.1);
}
</style>
