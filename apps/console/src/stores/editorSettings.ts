import { defineStore } from "pinia";
import { ref, watch } from "vue";

export type EditorTheme = "vs-dark" | "vs-light" | "hc-black";

export interface EditorSettings {
  theme: EditorTheme;
  fontSize: number;
  tabSize: number;
  wordWrap: boolean;
  minimap: boolean;
  lineNumbers: "on" | "off" | "relative";
  fontFamily: string;
  /** Accessibility: Reduce motion/animations */
  reduceMotion: boolean;
  /** Accessibility: High contrast mode */
  highContrast: boolean;
}

const STORAGE_KEY = "ulticode-editor-settings";

const DEFAULT_SETTINGS: EditorSettings = {
  theme: "vs-dark",
  fontSize: 14,
  tabSize: 2,
  wordWrap: false,
  minimap: false,
  lineNumbers: "on",
  fontFamily: '"JetBrains Mono", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", monospace',
  reduceMotion: false,
  highContrast: false,
};

function loadFromStorage(): EditorSettings {
  if (typeof window === "undefined") {
    return { ...DEFAULT_SETTINGS };
  }

  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      const parsed = JSON.parse(stored) as Partial<EditorSettings>;
      return {
        ...DEFAULT_SETTINGS,
        ...parsed,
      };
    }
  } catch {
    // Invalid JSON, use defaults
  }

  // Try to detect system preference for theme
  const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
  // Detect system preference for reduced motion
  const prefersReducedMotion = window.matchMedia(
    "(prefers-reduced-motion: reduce)",
  ).matches;
  // Detect system preference for high contrast
  const prefersHighContrast = window.matchMedia(
    "(prefers-contrast: more)",
  ).matches;

  return {
    ...DEFAULT_SETTINGS,
    theme: prefersDark ? "vs-dark" : "vs-light",
    reduceMotion: prefersReducedMotion,
    highContrast: prefersHighContrast,
  };
}

function saveToStorage(settings: EditorSettings): void {
  if (typeof window === "undefined") {
    return;
  }

  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // Storage quota exceeded or unavailable
  }
}

export const useEditorSettingsStore = defineStore("editorSettings", () => {
  const settings = ref<EditorSettings>(loadFromStorage());

  // Persist settings changes to localStorage
  watch(
    settings,
    (newSettings) => {
      saveToStorage(newSettings);
    },
    { deep: true },
  );

  // Theme
  const setTheme = (theme: EditorTheme) => {
    settings.value = { ...settings.value, theme };
  };

  // Font size (clamped to valid range)
  const setFontSize = (size: number) => {
    const clampedSize = Math.max(10, Math.min(24, size));
    settings.value = { ...settings.value, fontSize: clampedSize };
  };

  // Tab size
  const setTabSize = (size: number) => {
    const clampedSize = Math.max(1, Math.min(8, size));
    settings.value = { ...settings.value, tabSize: clampedSize };
  };

  // Word wrap
  const setWordWrap = (enabled: boolean) => {
    settings.value = { ...settings.value, wordWrap: enabled };
  };

  const toggleWordWrap = () => {
    settings.value = { ...settings.value, wordWrap: !settings.value.wordWrap };
  };

  // Minimap
  const setMinimap = (enabled: boolean) => {
    settings.value = { ...settings.value, minimap: enabled };
  };

  const toggleMinimap = () => {
    settings.value = { ...settings.value, minimap: !settings.value.minimap };
  };

  // Line numbers
  const setLineNumbers = (mode: "on" | "off" | "relative") => {
    settings.value = { ...settings.value, lineNumbers: mode };
  };

  // Font family
  const setFontFamily = (font: string) => {
    settings.value = { ...settings.value, fontFamily: font };
  };

  // Accessibility: Reduce motion
  const setReduceMotion = (enabled: boolean) => {
    settings.value = { ...settings.value, reduceMotion: enabled };
  };

  const toggleReduceMotion = () => {
    settings.value = {
      ...settings.value,
      reduceMotion: !settings.value.reduceMotion,
    };
  };

  // Accessibility: High contrast
  const setHighContrast = (enabled: boolean) => {
    settings.value = { ...settings.value, highContrast: enabled };
    // Also switch theme to hc-black when high contrast is enabled
    if (enabled) {
      settings.value = { ...settings.value, theme: "hc-black" };
    }
  };

  const toggleHighContrast = () => {
    const newValue = !settings.value.highContrast;
    settings.value = {
      ...settings.value,
      highContrast: newValue,
      theme: newValue ? "hc-black" : settings.value.theme,
    };
  };

  // Reset to defaults
  const resetToDefaults = () => {
    settings.value = { ...DEFAULT_SETTINGS };
  };

  // Batch update
  const updateSettings = (updates: Partial<EditorSettings>) => {
    settings.value = { ...settings.value, ...updates };
  };

  return {
    settings,
    setTheme,
    setFontSize,
    setTabSize,
    setWordWrap,
    toggleWordWrap,
    setMinimap,
    toggleMinimap,
    setLineNumbers,
    setFontFamily,
    setReduceMotion,
    toggleReduceMotion,
    setHighContrast,
    toggleHighContrast,
    resetToDefaults,
    updateSettings,
  };
});
