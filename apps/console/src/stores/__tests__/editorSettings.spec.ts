import { describe, it, expect, beforeEach, vi } from "vitest";
import { setActivePinia, createPinia } from "pinia";
import { useEditorSettingsStore } from "../editorSettings";

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] || null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key];
    }),
    clear: vi.fn(() => {
      store = {};
    }),
  };
})();

Object.defineProperty(window, "localStorage", {
  value: localStorageMock,
});

// Mock matchMedia - returns true for dark preference
Object.defineProperty(window, "matchMedia", {
  value: vi.fn().mockImplementation((query: string) => ({
    matches: query === "(prefers-color-scheme: dark)",
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

describe("useEditorSettingsStore", () => {
  beforeEach(() => {
    localStorageMock.clear();
    setActivePinia(createPinia());
  });

  describe("initial state", () => {
    it("should have default settings when localStorage is empty", () => {
      const store = useEditorSettingsStore();

      expect(store.settings.theme).toBe("vs-dark");
      expect(store.settings.fontSize).toBe(14);
      expect(store.settings.tabSize).toBe(2);
      expect(store.settings.wordWrap).toBe(false);
      expect(store.settings.minimap).toBe(false);
      expect(store.settings.lineNumbers).toBe("on");
    });

    it("should load settings from localStorage", () => {
      localStorageMock.setItem(
        "ulticode-editor-settings",
        JSON.stringify({
          theme: "vs-light",
          fontSize: 16,
          tabSize: 4,
          wordWrap: true,
        }),
      );

      setActivePinia(createPinia());
      const store = useEditorSettingsStore();

      expect(store.settings.theme).toBe("vs-light");
      expect(store.settings.fontSize).toBe(16);
      expect(store.settings.tabSize).toBe(4);
      expect(store.settings.wordWrap).toBe(true);
    });
  });

  describe("theme management", () => {
    it("should set theme", () => {
      const store = useEditorSettingsStore();

      store.setTheme("vs-light");
      expect(store.settings.theme).toBe("vs-light");

      store.setTheme("hc-black");
      expect(store.settings.theme).toBe("hc-black");
    });

    it("should persist theme to localStorage", () => {
      const store = useEditorSettingsStore();
      store.setTheme("vs-light");

      expect(localStorageMock.setItem).toHaveBeenCalled();
    });
  });

  describe("font size management", () => {
    it("should set font size", () => {
      const store = useEditorSettingsStore();

      store.setFontSize(18);
      expect(store.settings.fontSize).toBe(18);
    });

    it("should clamp font size to minimum 10", () => {
      const store = useEditorSettingsStore();

      store.setFontSize(5);
      expect(store.settings.fontSize).toBe(10);
    });

    it("should clamp font size to maximum 24", () => {
      const store = useEditorSettingsStore();

      store.setFontSize(30);
      expect(store.settings.fontSize).toBe(24);
    });
  });

  describe("tab size management", () => {
    it("should set tab size", () => {
      const store = useEditorSettingsStore();

      store.setTabSize(4);
      expect(store.settings.tabSize).toBe(4);
    });

    it("should clamp tab size to minimum 1", () => {
      const store = useEditorSettingsStore();

      store.setTabSize(0);
      expect(store.settings.tabSize).toBe(1);
    });

    it("should clamp tab size to maximum 8", () => {
      const store = useEditorSettingsStore();

      store.setTabSize(10);
      expect(store.settings.tabSize).toBe(8);
    });
  });

  describe("word wrap management", () => {
    it("should set word wrap", () => {
      const store = useEditorSettingsStore();

      store.setWordWrap(true);
      expect(store.settings.wordWrap).toBe(true);

      store.setWordWrap(false);
      expect(store.settings.wordWrap).toBe(false);
    });

    it("should toggle word wrap", () => {
      const store = useEditorSettingsStore();

      const initialValue = store.settings.wordWrap;
      store.toggleWordWrap();
      expect(store.settings.wordWrap).toBe(!initialValue);

      store.toggleWordWrap();
      expect(store.settings.wordWrap).toBe(initialValue);
    });
  });

  describe("minimap management", () => {
    it("should set minimap", () => {
      const store = useEditorSettingsStore();

      store.setMinimap(true);
      expect(store.settings.minimap).toBe(true);

      store.setMinimap(false);
      expect(store.settings.minimap).toBe(false);
    });

    it("should toggle minimap", () => {
      const store = useEditorSettingsStore();

      const initialValue = store.settings.minimap;
      store.toggleMinimap();
      expect(store.settings.minimap).toBe(!initialValue);

      store.toggleMinimap();
      expect(store.settings.minimap).toBe(initialValue);
    });
  });

  describe("line numbers management", () => {
    it("should set line numbers mode", () => {
      const store = useEditorSettingsStore();

      store.setLineNumbers("off");
      expect(store.settings.lineNumbers).toBe("off");

      store.setLineNumbers("relative");
      expect(store.settings.lineNumbers).toBe("relative");
    });
  });

  describe("font family management", () => {
    it("should set font family", () => {
      const store = useEditorSettingsStore();

      store.setFontFamily("Fira Code");
      expect(store.settings.fontFamily).toBe("Fira Code");
    });
  });

  describe("reset to defaults", () => {
    it("should reset all settings to defaults", () => {
      const store = useEditorSettingsStore();

      // Change some settings
      store.setTheme("vs-light");
      store.setFontSize(20);
      store.setTabSize(4);
      store.setWordWrap(true);
      store.setMinimap(true);

      // Reset
      store.resetToDefaults();

      expect(store.settings.theme).toBe("vs-dark");
      expect(store.settings.fontSize).toBe(14);
      expect(store.settings.tabSize).toBe(2);
      expect(store.settings.wordWrap).toBe(false);
      expect(store.settings.minimap).toBe(false);
    });
  });

  describe("batch update", () => {
    it("should update multiple settings at once", () => {
      const store = useEditorSettingsStore();

      store.updateSettings({
        theme: "vs-light",
        fontSize: 18,
        wordWrap: true,
      });

      expect(store.settings.theme).toBe("vs-light");
      expect(store.settings.fontSize).toBe(18);
      expect(store.settings.wordWrap).toBe(true);
    });
  });
});
