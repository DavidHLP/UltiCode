import { computed } from "vue";
import {
  useEditorSettingsStore,
  type EditorTheme,
} from "@/stores/editorSettings";

export interface ThemeOption {
  value: EditorTheme;
  label: string;
  description: string;
  icon: string;
}

export const AVAILABLE_THEMES: ThemeOption[] = [
  {
    value: "vs-dark",
    label: "Solarized Dark",
    description: "Solarized dark theme",
    icon: "moon",
  },
  {
    value: "vs-light",
    label: "Solarized Light",
    description: "Solarized light theme",
    icon: "sun",
  },
  {
    value: "hc-black",
    label: "High Contrast",
    description: "High contrast dark theme for accessibility",
    icon: "contrast",
  },
];

/**
 * Composable for managing Monaco editor themes
 *
 * Features:
 * - List available themes with metadata
 * - Apply theme to Monaco instance
 * - Sync with editor settings store
 * - System preference detection
 */
export function useEditorThemes() {
  const settingsStore = useEditorSettingsStore();

  const currentTheme = computed(() => settingsStore.settings.theme);

  const themeOptions = computed(() => AVAILABLE_THEMES);

  const currentThemeOption = computed(() =>
    AVAILABLE_THEMES.find((t) => t.value === currentTheme.value),
  );

  const setTheme = (theme: EditorTheme) => {
    settingsStore.setTheme(theme);
  };

  /**
   * Apply the current theme to a Monaco editor instance
   * Call this after Monaco is initialized
   */
  const applyThemeToMonaco = async (
    monaco: typeof import("monaco-editor"),
  ): Promise<void> => {
    monaco.editor.setTheme(currentTheme.value);
  };

  /**
   * Watch for theme changes and apply to Monaco
   * Returns a watcher that should be disposed on component unmount
   */
  const watchThemeChanges = (
    monaco: typeof import("monaco-editor") | null,
  ): (() => void) => {
    let lastTheme = currentTheme.value;

    const checkTheme = () => {
      if (!monaco) return;
      if (currentTheme.value !== lastTheme) {
        lastTheme = currentTheme.value;
        monaco.editor.setTheme(currentTheme.value);
      }
    };

    // Use a simple interval-based watcher since we can't watch computed directly
    // This is called frequently enough that it should be responsive
    const interval = setInterval(checkTheme, 100);

    return () => clearInterval(interval);
  };

  /**
   * Detect system color scheme preference
   */
  const detectSystemTheme = (): EditorTheme => {
    if (typeof window === "undefined") {
      return "vs-dark";
    }

    const prefersDark = window.matchMedia(
      "(prefers-color-scheme: dark)",
    ).matches;
    return prefersDark ? "vs-dark" : "vs-light";
  };

  /**
   * Set theme to match system preference
   */
  const useSystemTheme = () => {
    setTheme(detectSystemTheme());
  };

  /**
   * Check if current theme is dark
   */
  const isDarkTheme = computed(
    () => currentTheme.value === "vs-dark" || currentTheme.value === "hc-black",
  );

  return {
    currentTheme,
    themeOptions,
    currentThemeOption,
    setTheme,
    applyThemeToMonaco,
    watchThemeChanges,
    detectSystemTheme,
    useSystemTheme,
    isDarkTheme,
    availableThemes: AVAILABLE_THEMES,
  };
}
