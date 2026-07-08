<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { renderMarkdown } from '@/shared/markdown-utils/src'
import { toast } from 'vue-sonner'

/**
 * Problem description data model for markdown rendering.
 */
export interface ProblemDescription {
  /** Main description body */
  content: string
  /** Example list */
  examples?: Array<{
    input: string
    output: string
    explanation?: string
  }>
  /** Constraint bullets */
  constraints?: string[]
  /** Follow-up prompt */
  followUp?: string
}

const props = defineProps<{
  /** Problem description data */
  description: ProblemDescription
}>()

const { t } = useI18n()

const handleCopy = (e: MouseEvent) => {
  const target = (e.target as HTMLElement).closest('.lc-copy-btn')
  if (!target) return

  // Find the code block within the parent container
  const codeBlock = target.closest('.lc-code-block')?.querySelector('code')
  if (codeBlock) {
    const code = codeBlock.textContent || ''
    if (code) {
      navigator.clipboard.writeText(code)
      toast.success(t('problems.descriptionDisplay.codeCopied'))
    }
  }
}

/**
 * Compose the complete Markdown content for the viewer.
 */
const markdownContent = computed(() => {
  const parts: string[] = [props.description.content]

  // Add examples
  if (props.description.examples?.length) {
    parts.push('\n\n')

    props.description.examples.forEach((example, index) => {
      parts.push(
        `### ${t('problems.descriptionDisplay.example')} ${index + 1}\n`,
        `> **${t('problems.descriptionDisplay.input')}:** \`${example.input}\`\n>\n`,
        `> **${t('problems.descriptionDisplay.expectedOutput')}:** \`${example.output}\`\n`,
      )

      if (example.explanation) {
        parts.push(
          `>\n> **${t('problems.descriptionDisplay.explanation')}:** ${example.explanation}\n`,
        )
      }

      parts.push(`\n`)
    })
  }

  // Add constraints
  if (props.description.constraints?.length) {
    parts.push(
      `\n\n### ${t('problems.descriptionDisplay.constraints')}\n\n`,
      ...props.description.constraints.map((c) => `- ${c}\n`),
    )
  }

  // Add follow-up
  if (props.description.followUp) {
    parts.push(`\n\n### ${t('problems.descriptionDisplay.hints')}\n\n${props.description.followUp}`)
  }

  return parts.join('')
})

const htmlContent = computed(() => renderMarkdown(markdownContent.value))
</script>

<template>
  <div class="description-markdown" @click="handleCopy">
    <div class="markdown-content" v-html="htmlContent"></div>
  </div>
</template>

<style scoped>
.description-markdown :deep(.markdown-content) {
  font-size: var(--uc-text-sm);
  line-height: var(--uc-leading-normal);
  color: var(--foreground);
  font-family: var(--uc-font-prose);
}


.description-markdown :deep(.markdown-content h1) {
  font-size: var(--uc-text-2xl);
  font-weight: var(--uc-font-weight-semibold);
  margin-bottom: 1rem;
}

.description-markdown :deep(.markdown-content h2) {
  font-size: var(--uc-text-xl);
  font-weight: var(--uc-font-weight-semibold);
  margin-top: 1.5rem;
  margin-bottom: 0.75rem;
}

.description-markdown :deep(.markdown-content h3) {
  font-size: var(--uc-text-md);
  font-weight: var(--uc-font-weight-bold);
  margin-top: 1.5rem;
  margin-bottom: 0.75rem;
}

.description-markdown :deep(.markdown-content h4) {
  font-size: var(--uc-text-sm);
  font-weight: var(--uc-font-weight-bold);
  margin-top: 1rem;
  margin-bottom: 0.5rem;
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
  margin-bottom: 0.25rem;
}


.description-markdown :deep(.markdown-content code) {
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-xl);
  background-color: var(--surface-sunken);
  color: var(--foreground);
  padding: 0.125rem 0.375rem;
  border-radius: 0;
  white-space: pre-wrap;
  word-break: break-word;
}


.description-markdown :deep(.markdown-content pre) {
  background-color: var(--surface-sunken);
  border-radius: 0;
  padding: 1rem;
  margin: 1rem 0;
  overflow-x: auto;
  border: 1px solid var(--border);
}

.description-markdown :deep(.markdown-content pre code) {
  background-color: transparent;
  padding: 0;
  border-radius: 0;
  color: inherit;
  font-size: var(--uc-text-sm);
}


.description-markdown :deep(.markdown-content blockquote) {
  border-left: 3px solid var(--border);
  padding-left: 1rem;
  color: var(--muted-foreground);
  margin: 1rem 0;
}


.description-markdown :deep(.markdown-content strong) {
  font-weight: var(--uc-font-weight-semibold);}


.description-markdown :deep(.katex) {
  font-size: var(--uc-text-md);
}

.description-markdown :deep(.katex-display) {
  margin: 0.5rem 0;
  overflow-x: auto;
  overflow-y: hidden;
}


.description-markdown :deep(.lc-code-tabs) {
  margin: 1rem 0;
  border: 1px solid var(--border);
  border-radius: 0;
  overflow: hidden;
}

.description-markdown :deep(.lc-tabs-header) {
  display: flex;
  background: var(--surface-sunken);
  border-bottom: 1px solid var(--border);
}

.description-markdown :deep(.lc-tab-btn) {
  padding: 0.5rem 1rem;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  font-size: var(--uc-text-sm);
  transition: all 0.2s;
  color: var(--muted-foreground);
}

.description-markdown :deep(.lc-tab-btn:hover) {
  color: var(--foreground);
}

.description-markdown :deep(.lc-tab-btn.active) {
  color: var(--foreground);
  border-bottom-color: var(--primary);
  background: var(--background);
}

.description-markdown :deep(.lc-tabs-body) {
  position: relative;
}

.description-markdown :deep(.lc-code-panel) {
  display: none;
}

.description-markdown :deep(.lc-code-panel.active) {
  display: block;
}

.description-markdown :deep(.lc-code-block) {
  position: relative;
}

.description-markdown :deep(.lc-copy-wrapper) {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  z-index: 10;
}

.description-markdown :deep(.lc-copy-btn) {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.25rem;
  background: var(--surface-sunken);
  border: 1px solid var(--border);
  border-radius: 0;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
  color: var(--muted-foreground);
}

.description-markdown :deep(.lc-code-block:hover .lc-copy-btn) {
  opacity: 1;
}

.description-markdown :deep(.lc-copy-btn:hover) {
  background: var(--background);
  color: var(--foreground);
}

.description-markdown :deep(.lc-code-block pre) {
  margin: 0;
  padding: 1rem;
  padding-right: 3rem;
  background: transparent;
}

.description-markdown :deep(.lc-code-block code) {
  background: transparent;
  padding: 0;
}
</style>
