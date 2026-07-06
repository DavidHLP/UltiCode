<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  getTemplatesForLanguage,
  getTemplateCategories,
  type CodeTemplate,
} from "@/constants/codeTemplates";
import { FileCode, Plus } from "lucide-vue-next";

const { t } = useI18n();

const props = defineProps<{
  open: boolean;
  language: string;
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
  (e: "insert", template: CodeTemplate): void;
}>();

const isOpen = computed({
  get: () => props.open,
  set: (value) => emit("update:open", value),
});

const selectedTemplate = ref<CodeTemplate | null>(null);
const searchQuery = ref("");

const templates = computed(() => getTemplatesForLanguage(props.language));

const categories = getTemplateCategories();

const filteredTemplates = computed(() => {
  if (!searchQuery.value) return templates.value;

  const query = searchQuery.value.toLowerCase();
  return templates.value.filter(
    (t) =>
      t.name.toLowerCase().includes(query) ||
      t.description.toLowerCase().includes(query),
  );
});

const groupedTemplates = computed(() => {
  const groups: Record<string, CodeTemplate[]> = {};

  for (const template of filteredTemplates.value) {
    const category = template.category;
    if (!groups[category]) {
      groups[category] = [];
    }
    groups[category].push(template);
  }

  return Object.entries(groups).map(([category, items]) => ({
    category,
    label: categories.find((c) => c.id === category)?.label ?? category,
    items,
  }));
});

const handleSelect = (template: CodeTemplate) => {
  selectedTemplate.value = template;
};

const handleInsert = () => {
  if (selectedTemplate.value) {
    emit("insert", selectedTemplate.value);
    isOpen.value = false;
    selectedTemplate.value = null;
  }
};

const handleDoubleClick = (template: CodeTemplate) => {
  emit("insert", template);
  isOpen.value = false;
};

// Reset selection when modal opens
watch(isOpen, (open) => {
  if (open) {
    selectedTemplate.value = null;
    searchQuery.value = "";
  }
});
</script>

<template>
  <Dialog v-model:open="isOpen">
    <DialogContent class="max-w-4xl max-h-[80vh]">
      <DialogHeader>
        <DialogTitle>{{ t("problem.editor.templates.title") }}</DialogTitle>
        <DialogDescription>
          {{ t("problem.editor.templates.description") }}
        </DialogDescription>
      </DialogHeader>

      <div class="grid grid-cols-2 gap-4 mt-4 h-[400px]">
        <!-- Template List -->
        <div class="border rounded-none overflow-hidden flex flex-col">
          <div class="p-2 border-b bg-muted/50">
            <input
              v-model="searchQuery"
              type="text"
              :placeholder="t('problem.editor.templates.searchPlaceholder')"
              class="w-full px-3 py-1.5 text-sm bg-background border rounded-none outline-none focus:ring-2 focus:ring-ring"
            />
          </div>

          <ScrollArea class="flex-1">
            <div
              v-if="groupedTemplates.length === 0"
              class="p-4 text-center text-muted-foreground text-sm"
            >
              {{ t("problem.editor.templates.noTemplates") }}
            </div>

            <div v-else class="p-2 space-y-4">
              <div
                v-for="group in groupedTemplates"
                :key="group.category"
                class="space-y-1"
              >
                <h4
                  class="text-xs font-semibold text-muted-foreground px-2 py-1"
                >
                  {{ group.label }}
                </h4>
                <button
                  v-for="template in group.items"
                  :key="template.id"
                  class="w-full text-left px-3 py-2 rounded-none text-sm hover:bg-accent transition-colors"
                  :class="[
                    selectedTemplate?.id === template.id
                      ? 'bg-accent text-accent-foreground'
                      : '',
                  ]"
                  @click="handleSelect(template)"
                  @dblclick="handleDoubleClick(template)"
                >
                  <div class="flex items-center gap-2">
                    <FileCode class="h-4 w-4 text-muted-foreground" />
                    <span class="font-medium">{{ template.name }}</span>
                  </div>
                  <p class="text-xs text-muted-foreground mt-0.5 ml-6">
                    {{ template.description }}
                  </p>
                </button>
              </div>
            </div>
          </ScrollArea>
        </div>

        <!-- Template Preview -->
        <div class="border rounded-none overflow-hidden flex flex-col">
          <div class="p-2 border-b bg-muted/50">
            <span class="text-sm font-medium">{{
              t("problem.editor.templates.preview")
            }}</span>
          </div>

          <div
            v-if="!selectedTemplate"
            class="flex-1 flex items-center justify-center text-muted-foreground text-sm"
          >
            {{ t("problem.editor.templates.selectToPreview") }}
          </div>

          <ScrollArea v-else class="flex-1">
            <pre class="p-3 text-xs font-mono bg-muted/30 overflow-x-auto">{{
              selectedTemplate.code
            }}</pre>
          </ScrollArea>
        </div>
      </div>

      <div class="flex justify-end gap-2 mt-4">
        <Button variant="outline" @click="isOpen = false">{{
          t("common.actions.cancel")
        }}</Button>
        <Button :disabled="!selectedTemplate" @click="handleInsert">
          <Plus class="h-4 w-4 mr-1" />
          {{ t("problem.editor.templates.insert") }}
        </Button>
      </div>
    </DialogContent>
  </Dialog>
</template>
