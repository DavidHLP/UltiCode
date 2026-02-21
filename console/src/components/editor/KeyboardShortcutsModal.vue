<script setup lang="ts">
import { computed } from "vue";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";

const props = defineProps<{
  open: boolean;
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
}>();

const isOpen = computed({
  get: () => props.open,
  set: (value) => emit("update:open", value),
});

interface ShortcutItem {
  keys: string[];
  description: string;
  category: string;
}

const shortcuts: ShortcutItem[] = [
  // General
  { keys: ["Ctrl", "S"], description: "Save file", category: "General" },
  { keys: ["Ctrl", "Z"], description: "Undo", category: "General" },
  { keys: ["Ctrl", "Shift", "Z"], description: "Redo", category: "General" },
  { keys: ["Ctrl", "Y"], description: "Redo (alternate)", category: "General" },

  // Navigation
  { keys: ["Ctrl", "G"], description: "Go to line", category: "Navigation" },
  { keys: ["Ctrl", "P"], description: "Quick open file", category: "Navigation" },
  { keys: ["Ctrl", "Shift", "O"], description: "Go to symbol", category: "Navigation" },
  { keys: ["Alt", "←"], description: "Go back", category: "Navigation" },
  { keys: ["Alt", "→"], description: "Go forward", category: "Navigation" },

  // Editing
  { keys: ["Ctrl", "D"], description: "Select next occurrence", category: "Editing" },
  { keys: ["Ctrl", "Shift", "K"], description: "Delete line", category: "Editing" },
  { keys: ["Alt", "↑"], description: "Move line up", category: "Editing" },
  { keys: ["Alt", "↓"], description: "Move line down", category: "Editing" },
  { keys: ["Shift", "Alt", "↑"], description: "Copy line up", category: "Editing" },
  { keys: ["Shift", "Alt", "↓"], description: "Copy line down", category: "Editing" },
  { keys: ["Ctrl", "/"], description: "Toggle line comment", category: "Editing" },
  { keys: ["Shift", "Alt", "A"], description: "Toggle block comment", category: "Editing" },
  { keys: ["Ctrl", "Shift", "\\"], description: "Match bracket", category: "Editing" },

  // Code Actions
  { keys: ["Ctrl", "Space"], description: "Trigger suggestions", category: "Code Actions" },
  { keys: ["Ctrl", "Shift", "Space"], description: "Parameter hints", category: "Code Actions" },
  { keys: ["F12"], description: "Go to definition", category: "Code Actions" },
  { keys: ["Shift", "F12"], description: "Find references", category: "Code Actions" },
  { keys: ["F2"], description: "Rename symbol", category: "Code Actions" },
  { keys: ["Ctrl", "."], description: "Quick fix", category: "Code Actions" },
  { keys: ["Shift", "Alt", "F"], description: "Format document", category: "Code Actions" },
  { keys: ["Ctrl", "K", "Ctrl", "F"], description: "Format selection", category: "Code Actions" },

  // Selection
  { keys: ["Ctrl", "A"], description: "Select all", category: "Selection" },
  { keys: ["Ctrl", "L"], description: "Select current line", category: "Selection" },
  { keys: ["Ctrl", "Shift", "L"], description: "Select all occurrences", category: "Selection" },
  { keys: ["Alt", "Click"], description: "Multi-cursor", category: "Selection" },
  { keys: ["Ctrl", "Alt", "↑"], description: "Add cursor above", category: "Selection" },
  { keys: ["Ctrl", "Alt", "↓"], description: "Add cursor below", category: "Selection" },

  // View
  { keys: ["Ctrl", "+"], description: "Zoom in", category: "View" },
  { keys: ["Ctrl", "-"], description: "Zoom out", category: "View" },
  { keys: ["Ctrl", "0"], description: "Reset zoom", category: "View" },
  { keys: ["Ctrl", "B"], description: "Toggle sidebar", category: "View" },
  { keys: ["Ctrl", "J"], description: "Toggle panel", category: "View" },

  // Search
  { keys: ["Ctrl", "F"], description: "Find", category: "Search" },
  { keys: ["Ctrl", "H"], description: "Find and replace", category: "Search" },
  { keys: ["Ctrl", "Shift", "F"], description: "Find in files", category: "Search" },
  { keys: ["F3"], description: "Find next", category: "Search" },
  { keys: ["Shift", "F3"], description: "Find previous", category: "Search" },
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

  return Object.entries(groups).map(([category, items]) => ({
    category,
    items,
  }));
});

const isMac = typeof navigator !== "undefined" && /Mac/.test(navigator.platform);

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
        <DialogTitle>Keyboard Shortcuts</DialogTitle>
        <DialogDescription>
          Use these shortcuts to work more efficiently in the code editor.
        </DialogDescription>
      </DialogHeader>

      <div class="space-y-6 mt-4">
        <div
          v-for="group in groupedShortcuts"
          :key="group.category"
          class="space-y-2"
        >
          <h3 class="text-sm font-semibold text-foreground border-b pb-1">
            {{ group.category }}
          </h3>
          <div class="space-y-1">
            <div
              v-for="shortcut in group.items"
              :key="shortcut.description"
              class="flex items-center justify-between py-1.5"
            >
              <span class="text-sm text-muted-foreground">
                {{ shortcut.description }}
              </span>
              <div class="flex items-center gap-1">
                <kbd
                  v-for="(key, index) in shortcut.keys"
                  :key="index"
                  class="px-1.5 py-0.5 text-xs font-mono bg-muted border rounded"
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
