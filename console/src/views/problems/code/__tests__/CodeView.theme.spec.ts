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

describe("problem code editor toolbar theme styles", () => {
  it("uses semantic theme tokens instead of inverted silver palette values", () => {
    expect(codeViewSource).toContain('data-testid="editor-command-bar"');
    expect(codeViewSource).toContain("bg-card/70");
    expect(codeViewSource).toContain("bg-muted/60");
    expect(codeViewSource).not.toMatch(/dark:bg-\[var\(--silver-(800|900)\)\]/);
  });

  it("keeps editor settings buttons aligned with the command bar states", () => {
    expect(editorToolbarSource).toContain("rounded-none");
    expect(editorToolbarSource).toContain("data-[state=open]:bg-accent");
    expect(editorToolbarSource).toContain("hover:bg-accent");
  });

  it("uses the problem language label instead of mapping every style to JavaScript", () => {
    expect(codeViewSource).toContain("languageMeta.value?.label");
    expect(codeViewSource).not.toContain(
      'languageMeta.value.style === "typescript"',
    );
  });
});
