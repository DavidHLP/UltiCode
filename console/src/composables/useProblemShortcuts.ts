import { type Ref } from "vue";
import { useShortcut, type ShortcutHandler } from "./useGlobalShortcuts";

export interface ProblemShortcutsConfig {
  onSubmit?: () => void;
  onRun?: () => void;
  onRunTest?: () => void;
  onReset?: () => void;
  onFormat?: () => void;
  onToggleNotes?: () => void;
  onTogglePanel?: () => void;
  onSwitchTab?: (direction: "next" | "prev") => void;
  activeTab?: Ref<string>;
  tabs?: string[];
}

/**
 * Composable for problem view keyboard shortcuts
 * Provides standardized keyboard navigation for the coding problem interface
 */
export function useProblemShortcuts(config: ProblemShortcutsConfig) {
  const shortcuts: ShortcutHandler[] = [];

  // Submit code: Ctrl+Enter
  if (config.onSubmit) {
    shortcuts.push({
      key: "Enter",
      ctrl: true,
      handler: config.onSubmit,
      description: "Submit code",
    });
  }

  // Run code: F5 or Ctrl+Shift+Enter
  if (config.onRun) {
    shortcuts.push({
      key: "F5",
      handler: config.onRun,
      description: "Run code",
    });
    shortcuts.push({
      key: "Enter",
      ctrl: true,
      shift: true,
      handler: config.onRun,
      description: "Run code (alternate)",
    });
  }

  // Run test case: Ctrl+Shift+T
  if (config.onRunTest) {
    shortcuts.push({
      key: "t",
      ctrl: true,
      shift: true,
      handler: config.onRunTest,
      description: "Run test case",
    });
  }

  // Reset code: Ctrl+Shift+R
  if (config.onReset) {
    shortcuts.push({
      key: "r",
      ctrl: true,
      shift: true,
      handler: config.onReset,
      description: "Reset code",
    });
  }

  // Format code: Ctrl+Shift+F
  if (config.onFormat) {
    shortcuts.push({
      key: "f",
      ctrl: true,
      shift: true,
      handler: config.onFormat,
      description: "Format code",
    });
  }

  // Toggle notes: Ctrl+N
  if (config.onToggleNotes) {
    shortcuts.push({
      key: "n",
      ctrl: true,
      handler: config.onToggleNotes,
      description: "Toggle notes",
    });
  }

  // Toggle side panel: Ctrl+B
  if (config.onTogglePanel) {
    shortcuts.push({
      key: "b",
      ctrl: true,
      handler: config.onTogglePanel,
      description: "Toggle side panel",
    });
  }

  // Tab navigation: Ctrl+PageUp / Ctrl+PageDown
  if (config.onSwitchTab && config.activeTab && config.tabs) {
    shortcuts.push({
      key: "PageDown",
      ctrl: true,
      handler: () => config.onSwitchTab!("next"),
      description: "Next tab",
    });
    shortcuts.push({
      key: "PageUp",
      ctrl: true,
      handler: () => config.onSwitchTab!("prev"),
      description: "Previous tab",
    });
  }

  useShortcut(shortcuts);

  return {
    shortcuts,
  };
}

/**
 * Helper function to switch to next/previous tab
 */
export function switchTab(
  direction: "next" | "prev",
  activeTab: Ref<string>,
  tabs: string[],
) {
  const currentIndex = tabs.indexOf(activeTab.value);
  if (currentIndex === -1) return;

  let newIndex: number;
  if (direction === "next") {
    newIndex = (currentIndex + 1) % tabs.length;
  } else {
    newIndex = (currentIndex - 1 + tabs.length) % tabs.length;
  }

  activeTab.value = tabs[newIndex]!;
}
