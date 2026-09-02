import { describe, it, expect, beforeEach, vi } from "vitest";
import { setActivePinia, createPinia } from "pinia";
import {
  resolveAppEditorTheme,
  syncAppEditorTheme,
  useEditorThemes,
} from "../useEditorThemes";

// Mock matchMedia
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

describe("useEditorThemes", () => {
  beforeEach(() => {
    localStorageMock.clear();
    setActivePinia(createPinia());
  });

  describe("app theme synchronization", () => {
    it("maps explicit app themes to matching Monaco themes", () => {
      expect(resolveAppEditorTheme("light", true)).toBe("vs-light");
      expect(resolveAppEditorTheme("dark", false)).toBe("vs-dark");
    });

    it("uses the system preference when the app theme is system", () => {
      expect(resolveAppEditorTheme("system", false)).toBe("vs-light");
      expect(resolveAppEditorTheme("system", true)).toBe("vs-dark");
    });

    it("updates the editor theme unless high contrast is active", () => {
      const setTheme = vi.fn();

      syncAppEditorTheme(
        { highContrast: false, theme: "vs-dark" },
        "light",
        false,
        setTheme,
      );
      expect(setTheme).toHaveBeenCalledWith("vs-light");

      setTheme.mockClear();
      syncAppEditorTheme(
        { highContrast: true, theme: "vs-dark" },
        "light",
        false,
        setTheme,
      );
      expect(setTheme).not.toHaveBeenCalled();

      setTheme.mockClear();
      syncAppEditorTheme(
        { highContrast: false, theme: "hc-black" },
        "dark",
        false,
        setTheme,
      );
      expect(setTheme).not.toHaveBeenCalled();
    });
  });

  describe("theme options", () => {
    it("should provide available themes", () => {
      const { themeOptions, availableThemes } = useEditorThemes();

      expect(themeOptions.value).toHaveLength(3);
      expect(availableThemes).toHaveLength(3);

      const themeValues = availableThemes.map((t) => t.value);
      expect(themeValues).toContain("vs-dark");
      expect(themeValues).toContain("vs-light");
      expect(themeValues).toContain("hc-black");
    });

    it("should have correct theme metadata", () => {
      const { availableThemes } = useEditorThemes();

      const darkTheme = availableThemes.find((t) => t.value === "vs-dark");
      expect(darkTheme?.label).toBe("Solarized Dark");
      expect(darkTheme?.icon).toBe("moon");

      const lightTheme = availableThemes.find((t) => t.value === "vs-light");
      expect(lightTheme?.label).toBe("Solarized Light");
      expect(lightTheme?.icon).toBe("sun");

      const hcTheme = availableThemes.find((t) => t.value === "hc-black");
      expect(hcTheme?.label).toBe("High Contrast");
      expect(hcTheme?.icon).toBe("contrast");
    });
  });

  describe("current theme", () => {
    it("should return current theme from store", () => {
      const { currentTheme } = useEditorThemes();

      // Default theme is vs-dark
      expect(currentTheme.value).toBe("vs-dark");
    });

    it("should return current theme option", () => {
      const { currentThemeOption } = useEditorThemes();

      expect(currentThemeOption.value?.value).toBe("vs-dark");
    });
  });

  describe("set theme", () => {
    it("should update theme in store", () => {
      const { currentTheme, setTheme } = useEditorThemes();

      setTheme("vs-light");
      expect(currentTheme.value).toBe("vs-light");

      setTheme("hc-black");
      expect(currentTheme.value).toBe("hc-black");
    });
  });

  describe("isDarkTheme", () => {
    it("should return true for vs-dark", () => {
      const { isDarkTheme, setTheme } = useEditorThemes();

      setTheme("vs-dark");
      expect(isDarkTheme.value).toBe(true);
    });

    it("should return true for hc-black", () => {
      const { isDarkTheme, setTheme } = useEditorThemes();

      setTheme("hc-black");
      expect(isDarkTheme.value).toBe(true);
    });

    it("should return false for vs-light", () => {
      const { isDarkTheme, setTheme } = useEditorThemes();

      setTheme("vs-light");
      expect(isDarkTheme.value).toBe(false);
    });
  });

  describe("detect system theme", () => {
    it("should detect dark theme when system prefers dark", () => {
      const { detectSystemTheme } = useEditorThemes();

      // Our mock returns true for dark
      const theme = detectSystemTheme();
      expect(theme).toBe("vs-dark");
    });
  });

  describe("use system theme", () => {
    it("should set theme based on system preference", () => {
      const { currentTheme, useSystemTheme } = useEditorThemes();

      useSystemTheme();
      // Our mock prefers dark
      expect(currentTheme.value).toBe("vs-dark");
    });
  });

  describe("apply theme to monaco", () => {
    it("should call monaco.editor.setTheme with current theme", async () => {
      const { applyThemeToMonaco, setTheme } = useEditorThemes();

      setTheme("vs-light");

      const mockMonaco = {
        editor: {
          setTheme: vi.fn(),
        },
      };

      await applyThemeToMonaco(
        mockMonaco as unknown as typeof import("monaco-editor"),
      );

      expect(mockMonaco.editor.setTheme).toHaveBeenCalledWith("vs-light");
    });
  });
});
