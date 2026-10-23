<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { renderMarkdown } from '@ulticode/markdown-utils'
import { Button } from '@ulticode/design-system/ui/button'
import { Textarea } from '@ulticode/design-system/ui/textarea'
import { Bold, Italic, Code, Link, Image, Maximize2, Minimize2 } from '@lucide/vue'

const props = defineProps<{
  modelValue: string
  readonly?: boolean
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const { t } = useI18n()

const editorContent = ref(props.modelValue)
const isLocalUpdate = ref(false)
const isFullscreen = ref(false)
const editorRef = ref<InstanceType<typeof Textarea>>()
const editorElement = computed(() => editorRef.value?.$el as HTMLTextAreaElement | undefined)
const previewRef = ref<HTMLDivElement>()

const previewHtml = computed(() => renderMarkdown(editorContent.value))

// Sync with modelValue prop
watch(
  () => props.modelValue,
  (val) => {
    if (!isLocalUpdate.value && val !== editorContent.value) {
      editorContent.value = val
    }
    isLocalUpdate.value = false
  },
)

// Emit changes
watch(editorContent, (val) => {
  isLocalUpdate.value = true
  emit('update:modelValue', val)
})

// Sync scroll between editor and preview
const syncScroll = (source: 'editor' | 'preview') => {
  if (!editorElement.value || !previewRef.value) return

  const editor = editorElement.value
  const preview = previewRef.value

  const origin = source === 'editor' ? editor : preview
  const target = source === 'editor' ? preview : editor
  const range = origin.scrollHeight - origin.clientHeight
  const scrollPercentage = range > 0 ? origin.scrollTop / range : 0
  target.scrollTop = scrollPercentage * Math.max(target.scrollHeight - target.clientHeight, 0)
}

const insertMarkdown = (before: string, after: string = '', placeholder: string = '') => {
  const textarea = editorElement.value
  if (!textarea) return

  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const text = editorContent.value
  const selectedText = text.substring(start, end) || placeholder

  const newText = text.substring(0, start) + before + selectedText + after + text.substring(end)
  editorContent.value = newText

  // Set cursor position after the inserted text
  setTimeout(() => {
    textarea.focus()
    textarea.setSelectionRange(start + before.length, start + before.length + selectedText.length)
  }, 0)
}

const insertBold = () => insertMarkdown('**', '**', 'bold text')
const insertItalic = () => insertMarkdown('*', '*', 'italic text')
const insertCode = () => insertMarkdown('`', '`', 'code')
const insertLink = () => insertMarkdown('[', '](url)', 'link text')
const insertImage = () => insertMarkdown('![', '](url)', 'alt text')
const insertCodeBlock = () => insertMarkdown('\n```\n', '\n```\n', 'code')

const toggleFullscreen = () => {
  isFullscreen.value = !isFullscreen.value
}

// Handle escape key to exit fullscreen
const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape' && isFullscreen.value) {
    toggleFullscreen()
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div class="markdown-editor" :class="{ fullscreen: isFullscreen }" v-bind="$attrs">
    <!-- Toolbar -->
    <div class="toolbar">
      <div class="toolbar-group">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          class="w-7 p-0"
          @click="insertBold"
          :disabled="readonly"
          :title="t('problems.markdownEditor.bold')"
        >
          <Bold :size="16" />
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          class="w-7 p-0"
          @click="insertItalic"
          :disabled="readonly"
          :title="t('problems.markdownEditor.italic')"
        >
          <Italic :size="16" />
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          class="w-7 p-0"
          @click="insertCode"
          :disabled="readonly"
          :title="t('problems.markdownEditor.inlineCode')"
        >
          <Code :size="16" />
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          class="px-2"
          @click="insertCodeBlock"
          :disabled="readonly"
          :title="t('problems.markdownEditor.codeBlock')"
        >
          <Code :size="16" />
          <span class="text-xs ml-1">/block</span>
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          class="w-7 p-0"
          @click="insertLink"
          :disabled="readonly"
          :title="t('problems.markdownEditor.insertLink')"
        >
          <Link :size="16" />
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          class="w-7 p-0"
          @click="insertImage"
          :disabled="readonly"
          :title="t('problems.markdownEditor.insertImage')"
        >
          <Image :size="16" />
        </Button>
      </div>
      <div class="toolbar-group">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          class="w-7 p-0"
          @click="toggleFullscreen"
          :title="t('problems.markdownEditor.toggleFullscreen')"
        >
          <Maximize2 v-if="!isFullscreen" :size="16" />
          <Minimize2 v-else :size="16" />
        </Button>
      </div>
    </div>

    <!-- Editor and Preview -->
    <div class="editor-container">
      <!-- Editor -->
      <div class="editor-pane bg-card">
        <Textarea
          ref="editorRef"
          v-model="editorContent"
          :placeholder="placeholder || t('problems.markdownEditor.placeholder')"
          :readonly="readonly"
          class="editor-textarea"
          @scroll="syncScroll('editor')"
          @keydown="
            readonly
              ? undefined
              : (e: KeyboardEvent) => {
                  if (e.key === 'b' && e.ctrlKey) {
                    e.preventDefault()
                    insertBold()
                  } else if (e.key === 'i' && e.ctrlKey) {
                    e.preventDefault()
                    insertItalic()
                  }
                }
          "
        />
      </div>

      <!-- Preview -->
      <div class="preview-pane bg-background">
        <div
          ref="previewRef"
          class="preview-content"
          @scroll="syncScroll('preview')"
          v-html="previewHtml"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.markdown-editor {
  container: markdown-editor / inline-size;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--background);
}

.markdown-editor.fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 50;
  border-radius: var(--radius-md);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  flex-shrink: 0;
  gap: 0.5rem;
  align-items: center;
  justify-content: space-between;
  padding: 0.375rem 0.5rem;
  border-bottom: 1px solid var(--border);
  background: var(--muted);
}

.toolbar-group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.25rem;
}

.editor-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 400px;
  max-height: 600px;
  overflow: hidden;
}

.fullscreen .editor-container {
  max-height: none;
  min-height: 0;
  flex: 1;
}

.editor-pane,
.preview-pane {
  overflow: auto;
}

.editor-pane {
  border-inline-end: 1px solid var(--border);
}

@container markdown-editor (max-width: 36rem) {
  .editor-container {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
  }

  .editor-pane {
    border-inline-end: 0;
    border-block-end: 1px solid var(--border);
  }
}

.editor-textarea {
  flex: 1;
  height: 100%;
  min-height: 0;
  field-sizing: fixed;
  padding: 1rem;
  border: none;
  border-radius: var(--radius-md);
  resize: none;
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-sm);
  line-height: 1.6;
  background: var(--card);
  color: var(--foreground);
}

.editor-textarea:focus {
  outline: none;
}

.preview-content {
  height: 100%;
  overflow: auto;
  padding: 1rem;
}

.preview-content :deep(h1) {
  font-size: var(--uc-text-2xl);
  font-weight: var(--uc-font-weight-bold);
  margin-top: 1rem;
  margin-bottom: 0.75rem;
}

.preview-content :deep(h2) {
  font-size: var(--uc-text-xl);
  font-weight: var(--uc-font-weight-semibold);
  margin-top: 1.25rem;
  margin-bottom: 0.5rem;
}

.preview-content :deep(h3) {
  font-size: var(--uc-text-md);
  font-weight: var(--uc-font-weight-semibold);
  margin-top: 1rem;
  margin-bottom: 0.5rem;
}

.preview-content :deep(p) {
  margin-bottom: 0.75rem;
  line-height: 1.6;
}

.preview-content :deep(code) {
  background: var(--muted);
  padding: 0.125rem 0.25rem;
  border-radius: var(--radius-md);
  font-size: var(--uc-text-2xl);
  font-family: var(--uc-font-code);
}

.preview-content :deep(pre) {
  background: var(--muted);
  padding: 0.75rem;
  border-radius: var(--radius-md);
  overflow-x: auto;
  margin-bottom: 0.75rem;
  border: 1px solid var(--border);
}

.preview-content :deep(pre code) {
  background: transparent;
  padding: 0;
}

.preview-content :deep(ul),
.preview-content :deep(ol) {
  margin-left: 1.5rem;
  margin-bottom: 0.75rem;
}

.preview-content :deep(ul) {
  list-style-type: disc;
}

.preview-content :deep(ol) {
  list-style-type: decimal;
}

.preview-content :deep(li) {
  margin-bottom: 0.25rem;
}

.preview-content :deep(a) {
  color: var(--primary);
  text-decoration: underline;
}

.preview-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: var(--radius-md);
  margin: 0.5rem 0;
  border: 1px solid var(--border);
}

.preview-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 0.75rem;
}

.preview-content :deep(th),
.preview-content :deep(td) {
  border: 1px solid var(--border);
  padding: 0.5rem;
}

.preview-content :deep(th) {
  background: var(--muted);
}

.preview-content :deep(blockquote) {
  border-left: 4px solid var(--border);
  padding-left: 1rem;
  margin: 0.75rem 0;
  color: var(--muted-foreground);
}

.preview-content :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 1.5rem 0;
}
</style>
