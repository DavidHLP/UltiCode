/** Minimal semantic-token adapter for Monaco; high-contrast remains native. */
import type * as Monaco from "monaco-editor";
import { UI_PALETTE, readCssColor } from "@ulticode/design-system";

type TokenRule = { token: string; foreground?: string; fontStyle?: string };

const TOKEN_RULES: TokenRule[] = [
  { token: "keyword", foreground: UI_PALETTE.green, fontStyle: "bold" },
  { token: "keyword.flow", foreground: UI_PALETTE.green, fontStyle: "bold" },
  { token: "keyword.control", foreground: UI_PALETTE.green, fontStyle: "bold" },
  { token: "storage", foreground: UI_PALETTE.green, fontStyle: "bold" },
  { token: "storage.type", foreground: UI_PALETTE.green, fontStyle: "bold" },

  {
    token: "string",
    foreground: UI_PALETTE.cyan,
    fontStyle: "italic underline",
  },
  {
    token: "string.escape",
    foreground: UI_PALETTE.cyan,
    fontStyle: "underline",
  },

  { token: "number", foreground: UI_PALETTE.cyan, fontStyle: "underline" },
  { token: "constant", foreground: UI_PALETTE.orange, fontStyle: "underline" },
  {
    token: "constant.numeric",
    foreground: UI_PALETTE.cyan,
    fontStyle: "underline",
  },
  {
    token: "constant.language",
    foreground: UI_PALETTE.orange,
    fontStyle: "underline",
  },
  {
    token: "constant.character",
    foreground: UI_PALETTE.cyan,
    fontStyle: "underline",
  },
  {
    token: "constant.character.escape",
    foreground: UI_PALETTE.cyan,
    fontStyle: "underline",
  },

  { token: "comment", fontStyle: "italic" },
  { token: "comment.block", fontStyle: "italic" },
  { token: "comment.line", fontStyle: "italic" },

  { token: "type", foreground: UI_PALETTE.yellow, fontStyle: "underline" },
  {
    token: "type.identifier",
    foreground: UI_PALETTE.yellow,
    fontStyle: "underline",
  },
  { token: "class", foreground: UI_PALETTE.yellow, fontStyle: "underline" },
  {
    token: "constructor",
    foreground: UI_PALETTE.yellow,
    fontStyle: "bold underline",
  },

  { token: "identifier" },
  {
    token: "entity.name.function",
    foreground: UI_PALETTE.blue,
    fontStyle: "bold underline",
  },
  { token: "variable" },
  {
    token: "variable.predefined",
    foreground: UI_PALETTE.violet,
    fontStyle: "bold underline",
  },

  { token: "operator", fontStyle: "bold" },
  { token: "delimiter" },
  { token: "delimiter.html" },

  { token: "tag", foreground: UI_PALETTE.green, fontStyle: "bold underline" },
  {
    token: "tag.attribute.name",
    foreground: UI_PALETTE.yellow,
    fontStyle: "underline",
  },
  {
    token: "tag.attribute.value",
    foreground: UI_PALETTE.cyan,
    fontStyle: "italic underline",
  },

  {
    token: "regexp",
    foreground: UI_PALETTE.cyan,
    fontStyle: "italic underline",
  },
  {
    token: "regexp.constant.character.escape",
    foreground: UI_PALETTE.cyan,
    fontStyle: "underline",
  },

  { token: "decorator", foreground: UI_PALETTE.violet, fontStyle: "underline" },
  {
    token: "annotation",
    foreground: UI_PALETTE.violet,
    fontStyle: "underline",
  },

  { token: "invalid", foreground: UI_PALETTE.red, fontStyle: "underline" },
  { token: "error", foreground: UI_PALETTE.red, fontStyle: "underline" },
  {
    token: "meta.tag",
    foreground: UI_PALETTE.orange,
    fontStyle: "bold underline",
  },

  {
    token: "attribute.name",
    foreground: UI_PALETTE.yellow,
    fontStyle: "underline",
  },
  {
    token: "attribute.value",
    foreground: UI_PALETTE.cyan,
    fontStyle: "italic underline",
  },

  { token: "type.json", foreground: UI_PALETTE.yellow, fontStyle: "underline" },
  { token: "type.yaml", foreground: UI_PALETTE.yellow, fontStyle: "underline" },
];

export function registerAppThemes(monaco: typeof Monaco): void {
  const rootDark =
    typeof document !== "undefined" &&
    document.documentElement.classList.contains("dark");
  for (const dark of [false, true]) {
    const color = (token: string, fallback: string) =>
      dark === rootDark ? readCssColor(token, fallback) : fallback;
    const foreground = color(
      "--foreground",
      dark ? UI_PALETTE.darkForeground : UI_PALETTE.foreground,
    );
    const background = color(
      "--background",
      dark ? UI_PALETTE.darkBackground : UI_PALETTE.background,
    );
    const surface = color(
      "--muted",
      dark ? UI_PALETTE.darkCard : UI_PALETTE.secondary,
    );
    const border = color(
      "--border",
      dark ? UI_PALETTE.darkMuted : UI_PALETTE.border,
    );
    const muted = color(
      "--muted-foreground",
      dark ? UI_PALETTE.darkMuted : UI_PALETTE.muted,
    );
    const syntax: Record<string, string> = {
      [UI_PALETTE.green]: color(
        "--status-success-mark",
        dark ? UI_PALETTE.darkGreen : UI_PALETTE.green,
      ),
      [UI_PALETTE.blue]: color(
        "--status-info-mark",
        dark ? UI_PALETTE.darkBlue : UI_PALETTE.blue,
      ),
      [UI_PALETTE.cyan]: color(
        "--status-info-mark",
        dark ? UI_PALETTE.darkBlue : UI_PALETTE.blue,
      ),
      [UI_PALETTE.yellow]: color(
        "--status-warning-mark",
        dark ? UI_PALETTE.darkYellow : UI_PALETTE.yellow,
      ),
      [UI_PALETTE.orange]: color(
        "--status-warning-mark",
        dark ? UI_PALETTE.darkYellow : UI_PALETTE.yellow,
      ),
      [UI_PALETTE.violet]: color(
        "--status-special-mark",
        dark ? UI_PALETTE.darkViolet : UI_PALETTE.violet,
      ),
      [UI_PALETTE.red]: color(
        "--status-error-mark",
        dark ? UI_PALETTE.darkRed : UI_PALETTE.red,
      ),
    };
    const definition: Monaco.editor.IStandaloneThemeData = {
      base: dark ? "vs-dark" : "vs",
      inherit: true,
      rules: TOKEN_RULES.map((rule) => ({
        ...rule,
        foreground: rule.foreground ? syntax[rule.foreground] : foreground,
      })),
      colors: {
        "editor.background": background,
        "editor.foreground": foreground,
        "editorLineNumber.foreground": muted,
        "editorLineNumber.activeForeground": foreground,
        "editor.selectionBackground": surface,
        "editor.selectionForeground": foreground,
        "editorCursor.foreground": foreground,
        "editor.lineHighlightBackground": surface,
        "editorIndentGuide.background": border,
        "editorIndentGuide.activeBackground": muted,
        "editorBracketMatch.background": surface,
        "editorBracketMatch.border": border,
        "editorWidget.background": surface,
        "editorWidget.border": border,
        "editorSuggestWidget.background": surface,
        "editorSuggestWidget.border": border,
        "editorSuggestWidget.selectedBackground": border,
        "editorHoverWidget.background": surface,
        "editorHoverWidget.border": border,
        "scrollbar.shadow": border,
        "scrollbarSlider.background": border,
        "scrollbarSlider.hoverBackground": muted,
        "scrollbarSlider.activeBackground": foreground,
        "minimap.background": background,
        "input.background": surface,
        "input.border": border,
        "input.foreground": foreground,
        focusBorder: color("--ring", foreground),
        "list.activeSelectionBackground": surface,
        "list.hoverBackground": surface,
      },
    };
    monaco.editor.defineTheme(dark ? "vs-dark" : "vs-light", definition);
    if (!dark) monaco.editor.defineTheme("vs", definition);
  }
}

export function observeAppEditorAppearance(
  monaco: typeof Monaco,
  onChange: () => void,
): () => void {
  const observer = new MutationObserver(() => {
    registerAppThemes(monaco);
    onChange();
  });
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: [
      "class",
      "data-appearance-base",
      "data-appearance-accent",
      "data-appearance-font",
      "data-appearance-density",
    ],
  });
  return () => observer.disconnect();
}
