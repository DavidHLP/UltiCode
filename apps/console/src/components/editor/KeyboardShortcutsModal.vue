<script setup lang="ts">
import { computed } from "vue";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { useI18n } from "vue-i18n";
import { isModalOpen } from "@/composables/useGlobalShortcuts";

const props = defineProps<{
  open: boolean;
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
}>();

const { t } = useI18n();

const isOpen = computed({
  get: () => props.open,
  set: (value) => {
    isModalOpen.value = value;
    emit("update:open", value);
  },
});

// Sync with global state
isModalOpen.value = props.open;

interface ShortcutItem {
  keys: string[];
  descriptionKey: string;
  category: string;
}

const shortcuts: ShortcutItem[] = [
  // UltiCode Specific
  {
    keys: ["Ctrl", "Enter"],
    descriptionKey: "shortcuts.submitCode",
    category: "UltiCode",
  },
  {
    keys: ["F5"],
    descriptionKey: "shortcuts.runCode",
    category: "UltiCode",
  },
  {
    keys: ["Ctrl", "N"],
    descriptionKey: "shortcuts.toggleNotes",
    category: "UltiCode",
  },
  {
    keys: ["Ctrl", "Shift", "Enter"],
    descriptionKey: "shortcuts.runTest",
    category: "UltiCode",
  },
  {
    keys: ["Ctrl", "/"],
    descriptionKey: "shortcuts.showShortcuts",
    category: "UltiCode",
  },

  // General
  {
    keys: ["Ctrl", "S"],
    descriptionKey: "shortcuts.saveFile",
    category: "general",
  },
  {
    keys: ["Ctrl", "Z"],
    descriptionKey: "shortcuts.undo",
    category: "general",
  },
  {
    keys: ["Ctrl", "Shift", "Z"],
    descriptionKey: "shortcuts.redo",
    category: "general",
  },
  {
    keys: ["Ctrl", "Y"],
    descriptionKey: "shortcuts.redoAlt",
    category: "general",
  },

  // Navigation
  {
    keys: ["Ctrl", "G"],
    descriptionKey: "shortcuts.goToLine",
    category: "navigation",
  },
  {
    keys: ["Ctrl", "P"],
    descriptionKey: "shortcuts.quickOpen",
    category: "navigation",
  },
  {
    keys: ["Ctrl", "Shift", "O"],
    descriptionKey: "shortcuts.goToSymbol",
    category: "navigation",
  },
  {
    keys: ["Alt", "←"],
    descriptionKey: "shortcuts.goBack",
    category: "navigation",
  },
  {
    keys: ["Alt", "→"],
    descriptionKey: "shortcuts.goForward",
    category: "navigation",
  },

  // Editing
  {
    keys: ["Ctrl", "D"],
    descriptionKey: "shortcuts.selectNext",
    category: "editing",
  },
  {
    keys: ["Ctrl", "Shift", "K"],
    descriptionKey: "shortcuts.deleteLine",
    category: "editing",
  },
  {
    keys: ["Alt", "↑"],
    descriptionKey: "shortcuts.moveLineUp",
    category: "editing",
  },
  {
    keys: ["Alt", "↓"],
    descriptionKey: "shortcuts.moveLineDown",
    category: "editing",
  },
  {
    keys: ["Shift", "Alt", "↑"],
    descriptionKey: "shortcuts.copyLineUp",
    category: "editing",
  },
  {
    keys: ["Shift", "Alt", "↓"],
    descriptionKey: "shortcuts.copyLineDown",
    category: "editing",
  },
  {
    keys: ["Ctrl", "/"],
    descriptionKey: "shortcuts.toggleComment",
    category: "editing",
  },
  {
    keys: ["Shift", "Alt", "A"],
    descriptionKey: "shortcuts.toggleBlockComment",
    category: "editing",
  },
  {
    keys: ["Ctrl", "Shift", "\\"],
    descriptionKey: "shortcuts.matchBracket",
    category: "editing",
  },

  // Code Actions
  {
    keys: ["Ctrl", "Space"],
    descriptionKey: "shortcuts.triggerSuggestions",
    category: "codeActions",
  },
  {
    keys: ["Ctrl", "Shift", "Space"],
    descriptionKey: "shortcuts.parameterHints",
    category: "codeActions",
  },
  {
    keys: ["F12"],
    descriptionKey: "shortcuts.goToDefinition",
    category: "codeActions",
  },
  {
    keys: ["Shift", "F12"],
    descriptionKey: "shortcuts.findReferences",
    category: "codeActions",
  },
  {
    keys: ["F2"],
    descriptionKey: "shortcuts.renameSymbol",
    category: "codeActions",
  },
  {
    keys: ["Ctrl", "."],
    descriptionKey: "shortcuts.quickFix",
    category: "codeActions",
  },
  {
    keys: ["Shift", "Alt", "F"],
    descriptionKey: "shortcuts.formatDocument",
    category: "codeActions",
  },
  {
    keys: ["Ctrl", "K", "Ctrl", "F"],
    descriptionKey: "shortcuts.formatSelection",
    category: "codeActions",
  },

  // Selection
  {
    keys: ["Ctrl", "A"],
    descriptionKey: "shortcuts.selectAll",
    category: "selection",
  },
  {
    keys: ["Ctrl", "L"],
    descriptionKey: "shortcuts.selectLine",
    category: "selection",
  },
  {
    keys: ["Ctrl", "Shift", "L"],
    descriptionKey: "shortcuts.selectAllOccurrences",
    category: "selection",
  },
  {
    keys: ["Alt", "Click"],
    descriptionKey: "shortcuts.multiCursor",
    category: "selection",
  },
  {
    keys: ["Ctrl", "Alt", "↑"],
    descriptionKey: "shortcuts.addCursorAbove",
    category: "selection",
  },
  {
    keys: ["Ctrl", "Alt", "↓"],
    descriptionKey: "shortcuts.addCursorBelow",
    category: "selection",
  },

  // View
  { keys: ["Ctrl", "+"], descriptionKey: "shortcuts.zoomIn", category: "view" },
  {
    keys: ["Ctrl", "-"],
    descriptionKey: "shortcuts.zoomOut",
    category: "view",
  },
  {
    keys: ["Ctrl", "0"],
    descriptionKey: "shortcuts.resetZoom",
    category: "view",
  },
  {
    keys: ["Ctrl", "B"],
    descriptionKey: "shortcuts.toggleSidebar",
    category: "view",
  },
  {
    keys: ["Ctrl", "J"],
    descriptionKey: "shortcuts.togglePanel",
    category: "view",
  },

  // Search
  { keys: ["Ctrl", "F"], descriptionKey: "shortcuts.find", category: "search" },
  {
    keys: ["Ctrl", "H"],
    descriptionKey: "shortcuts.findReplace",
    category: "search",
  },
  {
    keys: ["Ctrl", "Shift", "F"],
    descriptionKey: "shortcuts.findInFiles",
    category: "search",
  },
  { keys: ["F3"], descriptionKey: "shortcuts.findNext", category: "search" },
  {
    keys: ["Shift", "F3"],
    descriptionKey: "shortcuts.findPrevious",
    category: "search",
  },
];

const groupedShortcuts = computed(() => {
  const groups: Record<string, ShortcutItem[]> = {};

  for (const shortcut of shortcuts) {
    if (!groups[shortcut.category]) {
      groups[shortcut.category] = [];
    }
    const group = groups[shortcut.category];
    if (group) {
      group.push(shortcut);
    }
  }

  // Define category order
  const categoryOrder = [
    "UltiCode",
    "general",
    "navigation",
    "editing",
    "codeActions",
    "selection",
    "view",
    "search",
  ];

  return categoryOrder
    .filter((cat) => groups[cat])
    .map((category) => ({
      category,
      categoryLabel: t(`shortcuts.categories.${category}`),
      items: groups[category] ?? [],
    }));
});

const isMac =
  typeof navigator !== "undefined" && /Mac/.test(navigator.platform);

const formatKey = (key: string): string => {
  if (!isMac) return key;

  const macMap: Record<string, string> = {
    Ctrl: "⌘",
    Alt: "⌥",
    Shift: "⇧",
    Enter: "↵",
    Tab: "⇥",
    Escape: "⎋",
    ArrowUp: "↑",
    ArrowDown: "↓",
    ArrowLeft: "←",
    ArrowRight: "→",
    Backspace: "⌫",
    Delete: "⌦",
  };

  return macMap[key] ?? key;
};
</script>

<template>
  <Dialog v-model:open="isOpen">
    <DialogContent class="max-w-2xl max-h-[80vh] overflow-y-auto">
      <DialogHeader>
        <DialogTitle>{{ t("shortcuts.title") }}</DialogTitle>
        <DialogDescription>
          {{ t("shortcuts.description") }}
        </DialogDescription>
      </DialogHeader>

      <div class="space-y-6 mt-4">
        <div
          v-for="group in groupedShortcuts"
          :key="group.category"
          class="space-y-2"
        >
          <h3 class="text-sm font-semibold text-foreground border-b pb-1">
            {{ group.categoryLabel }}
          </h3>
          <div class="space-y-1">
            <div
              v-for="shortcut in group.items"
              :key="shortcut.descriptionKey"
              class="flex items-center justify-between py-1.5"
            >
              <span class="text-sm text-muted-foreground">
                {{ t(shortcut.descriptionKey) }}
              </span>
              <div class="flex items-center gap-1">
                <kbd
                  v-for="(key, index) in shortcut.keys"
                  :key="index"
                  class="rounded-none border border-border-control bg-[var(--surface-sunken)] px-1.5 py-0.5 text-xs text-muted-foreground font-data"
                >
                  {{ formatKey(key) }}
                </kbd>
              </div>
            </div>
          </div>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
