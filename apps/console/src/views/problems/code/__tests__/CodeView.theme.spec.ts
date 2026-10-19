import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const codeViewSource = readFileSync(
  resolve(process.cwd(), "src/views/problems/code/CodeView.vue"),
  "utf8",
);

const editorToolbarSource = readFileSync(
  resolve(process.cwd(), "src/components/editor/EditorToolbar.vue"),
  "utf8",
);

const shortcutsSource = readFileSync(
  resolve(process.cwd(), "src/components/editor/KeyboardShortcutsModal.vue"),
  "utf8",
);

const detailSource = readFileSync(
  resolve(process.cwd(), "src/views/problems/ProblemDetailView.vue"),
  "utf8",
);

describe("problem code editor toolbar theme styles", () => {
  it("keeps header groups in flow below the wide desktop breakpoint", () => {
    expect(detailSource).toContain("xl:h-12 xl:flex-nowrap xl:py-0");
    expect(detailSource).toContain("xl:absolute xl:inset-0 xl:w-auto");
    expect(detailSource).not.toContain("md:absolute");
  });

  it("preserves viewport gutters on the shared shortcuts dialog", () => {
    expect(shortcutsSource).toContain(
      '<DialogContent class="sm:max-w-2xl max-h-[80vh] overflow-y-auto">',
    );
  });

  it("wraps command groups without stretching them beyond the header", () => {
    const actionClasses = codeViewSource
      .match(/class="([^"]*\beditor-command-actions\b[^"]*)"/)?.[1]
      .split(/\s+/);

    expect(actionClasses).toEqual(
      expect.arrayContaining([
        "flex",
        "flex-wrap",
        "items-center",
        "gap-1",
        "text-muted-foreground",
      ]),
    );
    expect(actionClasses).not.toContain("h-full");
  });

  it("uses semantic theme tokens instead of inverted silver palette values", () => {
    expect(codeViewSource).toContain('data-testid="editor-command-bar"');
    expect(codeViewSource).toContain("bg-card/70");
    expect(codeViewSource).toContain("bg-muted/60");
    expect(codeViewSource).not.toMatch(/dark:bg-\[var\(--silver-(800|900)\)\]/);
  });

  it("keeps editor settings buttons aligned with the command bar states", () => {
    expect(editorToolbarSource).toContain("@ulticode/design-system/ui/button");
    expect(editorToolbarSource).toContain('variant="ghost"');
    expect(editorToolbarSource).not.toContain("terminal-button");
  });

  it("uses the problem language label instead of mapping every style to JavaScript", () => {
    expect(codeViewSource).toContain("languageMeta.value?.label");
    expect(codeViewSource).not.toContain(
      'languageMeta.value.style === "typescript"',
    );
  });
});
