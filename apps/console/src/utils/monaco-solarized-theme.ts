/**
 * Solarized theme definitions for Monaco Editor.
 *
 * Registers custom themes that override Monaco's built-in "vs" / "vs-light"
 * and "vs-dark" identifiers so the editor palette matches the project's
 * Solarized design system without any type or store changes.
 *
 * Standard Solarized palette (Ethan Schoonover):
 *   8 monotones (base03–base3) + 8 accent colors
 *   Light: base3 background, accessible base01 foreground
 *   Dark:  base03 background, base0 foreground
 */

import type * as Monaco from "monaco-editor";

import { SOLARIZED_PALETTE } from "@ulticode/design-system";

const SOLARIZED = SOLARIZED_PALETTE;

type TokenRule = {
  token: string;
  foreground?: string;
  fontStyle?: string;
};

// ---------------------------------------------------------------------------
// Syntax token rules. Accent colors carry the Solarized language roles while
// font weight/underline/italic cues keep syntax meaning available without hue.
// ---------------------------------------------------------------------------

const TOKEN_RULES: TokenRule[] = [
  { token: "keyword", foreground: SOLARIZED.green, fontStyle: "bold" },
  { token: "keyword.flow", foreground: SOLARIZED.green, fontStyle: "bold" },
  { token: "keyword.control", foreground: SOLARIZED.green, fontStyle: "bold" },
  { token: "storage", foreground: SOLARIZED.green, fontStyle: "bold" },
  { token: "storage.type", foreground: SOLARIZED.green, fontStyle: "bold" },

  { token: "string", foreground: SOLARIZED.cyan, fontStyle: "italic underline" },
  { token: "string.escape", foreground: SOLARIZED.cyan, fontStyle: "underline" },

  { token: "number", foreground: SOLARIZED.cyan, fontStyle: "underline" },
  { token: "constant", foreground: SOLARIZED.orange, fontStyle: "underline" },
  { token: "constant.numeric", foreground: SOLARIZED.cyan, fontStyle: "underline" },
  { token: "constant.language", foreground: SOLARIZED.orange, fontStyle: "underline" },
  { token: "constant.character", foreground: SOLARIZED.cyan, fontStyle: "underline" },
  {
    token: "constant.character.escape",
    foreground: SOLARIZED.cyan,
    fontStyle: "underline",
  },

  { token: "comment", fontStyle: "italic" },
  { token: "comment.block", fontStyle: "italic" },
  { token: "comment.line", fontStyle: "italic" },

  { token: "type", foreground: SOLARIZED.yellow, fontStyle: "underline" },
  { token: "type.identifier", foreground: SOLARIZED.yellow, fontStyle: "underline" },
  { token: "class", foreground: SOLARIZED.yellow, fontStyle: "underline" },
  { token: "constructor", foreground: SOLARIZED.yellow, fontStyle: "bold underline" },

  { token: "identifier" },
  { token: "entity.name.function", foreground: SOLARIZED.blue, fontStyle: "bold underline" },
  { token: "variable" },
  { token: "variable.predefined", foreground: SOLARIZED.violet, fontStyle: "bold underline" },

  { token: "operator", fontStyle: "bold" },
  { token: "delimiter" },
  { token: "delimiter.html" },

  { token: "tag", foreground: SOLARIZED.green, fontStyle: "bold underline" },
  { token: "tag.attribute.name", foreground: SOLARIZED.yellow, fontStyle: "underline" },
  { token: "tag.attribute.value", foreground: SOLARIZED.cyan, fontStyle: "italic underline" },

  { token: "regexp", foreground: SOLARIZED.cyan, fontStyle: "italic underline" },
  {
    token: "regexp.constant.character.escape",
    foreground: SOLARIZED.cyan,
    fontStyle: "underline",
  },

  { token: "decorator", foreground: SOLARIZED.violet, fontStyle: "underline" },
  { token: "annotation", foreground: SOLARIZED.violet, fontStyle: "underline" },

  { token: "invalid", foreground: SOLARIZED.red, fontStyle: "underline" },
  { token: "error", foreground: SOLARIZED.red, fontStyle: "underline" },
  { token: "meta.tag", foreground: SOLARIZED.orange, fontStyle: "bold underline" },

  { token: "attribute.name", foreground: SOLARIZED.yellow, fontStyle: "underline" },
  { token: "attribute.value", foreground: SOLARIZED.cyan, fontStyle: "italic underline" },

  { token: "type.json", foreground: SOLARIZED.yellow, fontStyle: "underline" },
  { token: "type.yaml", foreground: SOLARIZED.yellow, fontStyle: "underline" },
];

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
  monaco: typeof Monaco,
): void {
  const lightRules = TOKEN_RULES.map((rule) => ({
    ...rule,
    foreground: rule.foreground ?? SOLARIZED.base01,
  }));

  const darkRules = TOKEN_RULES.map((rule) => ({
    ...rule,
    foreground: rule.foreground ?? SOLARIZED.base0,
  }));

  // --- Solarized Light ---

  const lightTheme = {
    base: "vs" as const,
    inherit: true,
    rules: lightRules,
    colors: {
      "editor.background": SOLARIZED.base3,
      "editor.foreground": SOLARIZED.base01,
      "editorLineNumber.foreground": SOLARIZED.base01,
      "editorLineNumber.activeForeground": SOLARIZED.base01,
      "editor.selectionBackground": `${SOLARIZED.base2}80`,
      "editor.selectionForeground": SOLARIZED.base01,
      "editorCursor.foreground": SOLARIZED.base01,
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
      "input.border": SOLARIZED.base00,
      "input.foreground": SOLARIZED.base01,
      focusBorder: SOLARIZED.base00,
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
      "editorLineNumber.foreground": SOLARIZED.base0,
      "editorLineNumber.activeForeground": SOLARIZED.base1,
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
      "input.border": SOLARIZED.base0,
      "input.foreground": SOLARIZED.base0,
      focusBorder: SOLARIZED.base0,
      "list.activeSelectionBackground": `${SOLARIZED.base01}60`,
      "list.hoverBackground": `${SOLARIZED.base01}30`,
    },
  };

  monaco.editor.defineTheme("vs-dark", darkTheme);
}
