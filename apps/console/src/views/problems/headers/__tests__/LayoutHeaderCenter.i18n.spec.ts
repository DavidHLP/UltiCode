import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

function readSource(path: string) {
  return readFileSync(resolve(process.cwd(), `src/${path}`), "utf8");
}

describe("LayoutHeaderCenter i18n keys", () => {
  it("defines run and submit guard messages in both locales", () => {
    for (const locale of ["zh-CN", "en-US"]) {
      const source = readSource(`i18n/locales/${locale}/problem.ts`);
      expect(source).toContain("loginRequired:");
      expect(source).toContain("codeRequired:");
    }
  });

  it("uses the code-specific empty submission message", () => {
    const source = readSource("views/problems/headers/LayoutHeaderCenter.vue");
    expect(source).toContain('t("problem.messages.codeRequired")');
    expect(source).not.toContain('t("problem.messages.enterTitle")');
  });

  it("does not require authentication before requesting a sample run", () => {
    const source = readSource("views/problems/headers/LayoutHeaderCenter.vue");
    const handleRunBody = source.match(
      /const handleRun = \(\) => \{([\s\S]*?)\n\};/,
    )?.[1];

    expect(handleRunBody).toContain("requestRun();");
    expect(handleRunBody).not.toContain("useAuthStore");
    expect(handleRunBody).not.toContain("loginRequired");
  });
});
