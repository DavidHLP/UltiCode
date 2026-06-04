/**
 * Solarized theme definitions for Monaco Editor.
 *
 * Registers custom themes that override Monaco's built-in "vs" / "vs-light"
 * and "vs-dark" identifiers so the editor palette matches the project's
 * Solarized design system without any type or store changes.
 *
 * Standard Solarized palette (Ethan Schoonover):
 *   8 monotones (base03–base3) + 8 accent colors
 *   Light: base3 background, base00 foreground
 *   Dark:  base03 background, base0 foreground
 */

// ---------------------------------------------------------------------------
// Solarized hex values
// ---------------------------------------------------------------------------

const SOLARIZED = {
  base03: "#002b36",
  base02: "#073642",
  base01: "#586e75",
  base00: "#657b83",
  base0: "#839496",
  base1: "#93a1a1",
  base2: "#eee8d5",
  base3: "#fdf6e3",
  yellow: "#b58900",
  orange: "#cb4b16",
  red: "#dc322f",
  magenta: "#d33682",
  violet: "#6c71c4",
  blue: "#268bd2",
  cyan: "#2aa198",
  green: "#859900",
} as const;

// ---------------------------------------------------------------------------
// Syntax token rules (identical for light & dark — only accent colors)
// ---------------------------------------------------------------------------

const TOKEN_RULES: { token: string; foreground: string; fontStyle?: string }[] =
  [
    // Keywords & control flow
    { token: "keyword", foreground: SOLARIZED.green },
    { token: "keyword.flow", foreground: SOLARIZED.green },
    { token: "keyword.control", foreground: SOLARIZED.green },
    { token: "storage", foreground: SOLARIZED.green },
    { token: "storage.type", foreground: SOLARIZED.green },

    // Strings
    { token: "string", foreground: SOLARIZED.cyan },
    { token: "string.escape", foreground: SOLARIZED.red },

    // Numbers & constants
    { token: "number", foreground: SOLARIZED.blue },
    { token: "constant", foreground: SOLARIZED.blue },
    { token: "constant.numeric", foreground: SOLARIZED.blue },
    { token: "constant.language", foreground: SOLARIZED.blue },
    { token: "constant.character", foreground: SOLARIZED.blue },
    { token: "constant.character.escape", foreground: SOLARIZED.red },

    // Comments
    { token: "comment", foreground: "", fontStyle: "italic" },
    { token: "comment.block", foreground: "", fontStyle: "italic" },
    { token: "comment.line", foreground: "", fontStyle: "italic" },

    // Types & classes
    { token: "type", foreground: SOLARIZED.yellow },
    { token: "type.identifier", foreground: SOLARIZED.yellow },
    { token: "class", foreground: SOLARIZED.yellow },
    { token: "constructor", foreground: SOLARIZED.blue },

    // Functions
    { token: "identifier", foreground: "" },
    { token: "entity.name.function", foreground: SOLARIZED.blue },
    { token: "variable", foreground: "" },
    { token: "variable.predefined", foreground: SOLARIZED.blue },

    // Operators & punctuation
    { token: "operator", foreground: SOLARIZED.green },
    { token: "delimiter", foreground: "" },
    { token: "delimiter.html", foreground: "" },

    // Tags (HTML / JSX)
    { token: "tag", foreground: SOLARIZED.blue },
    { token: "tag.attribute.name", foreground: SOLARIZED.yellow },
    { token: "tag.attribute.value", foreground: SOLARIZED.cyan },

    // Regex
    { token: "regexp", foreground: SOLARIZED.red },
    { token: "regexp.constant.character.escape", foreground: SOLARIZED.red },

    // Decorators / annotations
    { token: "decorator", foreground: SOLARIZED.magenta },
    { token: "annotation", foreground: SOLARIZED.magenta },

    // Meta (markdown, etc.)
    { token: "meta.tag", foreground: SOLARIZED.blue },

    // CSS / SCSS
    { token: "attribute.name", foreground: SOLARIZED.blue },
    { token: "attribute.value", foreground: SOLARIZED.cyan },

    // JSON keys
    { token: "type.json", foreground: SOLARIZED.blue },

    // YAML
    { token: "type.yaml", foreground: SOLARIZED.blue },
  ] as const;

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Register Solarized Light and Solarized Dark themes with a Monaco instance.
 *
 * Overrides the built-in "vs", "vs-light", and "vs-dark" theme identifiers.
 * "hc-black" is intentionally left untouched for accessibility.
 */
export function registerSolarizedThemes(
  monaco: typeof import("monaco-editor"),
): void {
  const lightRules = TOKEN_RULES.map((rule) => ({
    ...rule,
    // Comments & identifiers use the base01 foreground in light mode
    foreground: rule.foreground || SOLARIZED.base01,
  }));

  const darkRules = TOKEN_RULES.map((rule) => ({
    ...rule,
    // Comments & identifiers use the base01 foreground in dark mode
    foreground: rule.foreground || SOLARIZED.base01,
  }));

  // --- Solarized Light ---

  const lightTheme = {
    base: "vs" as const,
    inherit: true,
    rules: lightRules,
    colors: {
      "editor.background": SOLARIZED.base3,
      "editor.foreground": SOLARIZED.base00,
      "editorLineNumber.foreground": SOLARIZED.base1,
      "editorLineNumber.activeForeground": SOLARIZED.base01,
      "editor.selectionBackground": `${SOLARIZED.base2}80`,
      "editor.selectionForeground": SOLARIZED.base00,
      "editorCursor.foreground": SOLARIZED.base00,
      "editor.lineHighlightBackground": `${SOLARIZED.base2}40`,
      "editorIndentGuide.background": SOLARIZED.base2,
      "editorIndentGuide.activeBackground": SOLARIZED.base1,
      "editorBracketMatch.background": `${SOLARIZED.base2}80`,
      "editorBracketMatch.border": SOLARIZED.base1,
      "editorWidget.background": SOLARIZED.base2,
      "editorWidget.border": SOLARIZED.base1,
      "editorSuggestWidget.background": SOLARIZED.base2,
      "editorSuggestWidget.border": SOLARIZED.base1,
      "editorSuggestWidget.selectedBackground": `${SOLARIZED.base1}60`,
      "editorHoverWidget.background": SOLARIZED.base2,
      "editorHoverWidget.border": SOLARIZED.base1,
      "scrollbar.shadow": `${SOLARIZED.base1}20`,
      "scrollbarSlider.background": `${SOLARIZED.base1}30`,
      "scrollbarSlider.hoverBackground": `${SOLARIZED.base1}50`,
      "scrollbarSlider.activeBackground": `${SOLARIZED.base1}70`,
      "minimap.background": SOLARIZED.base2,
      "input.background": SOLARIZED.base3,
      "input.border": SOLARIZED.base1,
      "input.foreground": SOLARIZED.base00,
      focusBorder: SOLARIZED.blue,
      "list.activeSelectionBackground": `${SOLARIZED.base1}60`,
      "list.hoverBackground": `${SOLARIZED.base1}30`,
    },
  };

  monaco.editor.defineTheme("vs-light", lightTheme);
  monaco.editor.defineTheme("vs", lightTheme);

  // --- Solarized Dark ---

  const darkTheme = {
    base: "vs-dark" as const,
    inherit: true,
    rules: darkRules,
    colors: {
      "editor.background": SOLARIZED.base03,
      "editor.foreground": SOLARIZED.base0,
      "editorLineNumber.foreground": SOLARIZED.base01,
      "editorLineNumber.activeForeground": SOLARIZED.base0,
      "editor.selectionBackground": `${SOLARIZED.base02}80`,
      "editor.selectionForeground": SOLARIZED.base0,
      "editorCursor.foreground": SOLARIZED.base0,
      "editor.lineHighlightBackground": `${SOLARIZED.base02}40`,
      "editorIndentGuide.background": SOLARIZED.base02,
      "editorIndentGuide.activeBackground": SOLARIZED.base01,
      "editorBracketMatch.background": `${SOLARIZED.base02}80`,
      "editorBracketMatch.border": SOLARIZED.base01,
      "editorWidget.background": SOLARIZED.base02,
      "editorWidget.border": SOLARIZED.base01,
      "editorSuggestWidget.background": SOLARIZED.base02,
      "editorSuggestWidget.border": SOLARIZED.base01,
      "editorSuggestWidget.selectedBackground": `${SOLARIZED.base01}60`,
      "editorHoverWidget.background": SOLARIZED.base02,
      "editorHoverWidget.border": SOLARIZED.base01,
      "scrollbar.shadow": `${SOLARIZED.base01}20`,
      "scrollbarSlider.background": `${SOLARIZED.base01}30`,
      "scrollbarSlider.hoverBackground": `${SOLARIZED.base01}50`,
      "scrollbarSlider.activeBackground": `${SOLARIZED.base01}70`,
      "minimap.background": SOLARIZED.base02,
      "input.background": SOLARIZED.base03,
      "input.border": SOLARIZED.base01,
      "input.foreground": SOLARIZED.base0,
      focusBorder: SOLARIZED.blue,
      "list.activeSelectionBackground": `${SOLARIZED.base01}60`,
      "list.hoverBackground": `${SOLARIZED.base01}30`,
    },
  };

  monaco.editor.defineTheme("vs-dark", darkTheme);
}
