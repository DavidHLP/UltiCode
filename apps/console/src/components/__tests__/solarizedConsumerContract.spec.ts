import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import type * as Monaco from "monaco-editor";
import { registerSolarizedThemes } from "../../utils/monaco-solarized-theme";

function source(relativePath: string): string {
  return readFileSync(
    fileURLToPath(new URL(relativePath, import.meta.url)),
    "utf8",
  );
}

describe("Solarized component consumers", () => {
  it("keeps completed and selected calendar dates readable", () => {
    const calendar = source("../../components/ui/calendar/Calendar.vue");
    const trigger = source(
      "../../components/ui/calendar/CalendarCellTrigger.vue",
    );

    expect(calendar).toContain(
      "bg-status-warning-surface text-foreground-strong border border-[var(--status-warning-mark)]",
    );
    expect(trigger).toContain(
      "data-[selected]:bg-primary data-[selected]:text-primary-foreground",
    );
    expect(trigger).toContain("data-[selected]:border-link-decoration");
    expect(trigger).toContain("bg-surface-highlight");
  });

  it("uses neutral card text with semantic value and trend markers", () => {
    const stats = source("../../components/dashboard/StatsCard.vue");

    expect(stats).toContain("text-foreground-strong decoration-status-success-mark");
    expect(stats).toContain(
      "text-foreground-strong decoration-link-decoration",
    );
    expect(stats).toContain("text-foreground decoration-status-error-mark");
  });

  it("keeps activity status text neutral and accents icons or underlines", () => {
    const activity = source("../../components/dashboard/RecentActivity.vue");

    expect(activity).toContain(
      'success: "text-foreground decoration-status-success-mark',
    );
    expect(activity).toContain(
      'error: "text-foreground decoration-status-error-mark',
    );
    expect(activity).toContain('electric: "text-[var(--accent-primary)]"');
    expect(activity).not.toContain("--solarized-");
  });

  it("uses semantic surfaces and readable text in achievement toasts", () => {
    const toast = source("../../components/achievement/UnlockToast.vue");

    expect(toast).toContain('"var(--status-warning-mark)"');
    expect(toast).toContain('"var(--accent-primary)"');
    expect(toast).toContain("bg-status-warning-surface");
    expect(toast).toContain("text-muted-foreground");
    expect(toast).not.toMatch(
      /--solarized-|terminal-amber|text-gray|text-white/,
    );
  });

  it("uses adaptive foregrounds on status-mark controls", () => {
    const solutionList = source(
      "../../views/problems/solutions/SolutionListView.vue",
    );
    const switchSource = source("../../components/ui/switch/Switch.vue");

    expect(solutionList).toContain("text-primary-control-foreground");
    expect(switchSource).toContain(
      "data-[state=checked]:bg-primary-control-foreground",
    );
    expect(switchSource).not.toContain(
      "dark:data-[state=checked]:bg-primary-foreground",
    );
  });

  it("routes problem difficulty badges through the public semantic helper", () => {
    const resultList = source(
      "../../components/problem/components/ProblemResultList.vue",
    );

    expect(resultList).toContain(
      'import { getDifficultyBadgeClass } from "@ulticode/design-system"',
    );
    expect(resultList).toContain(
      "getDifficultyBadgeClass((problem as EnrichedProblem).difficulty)",
    );
    expect(resultList).not.toMatch(
      /oklch\(|--solarized-|terminal-(green|amber|red)/,
    );
  });
  it("uses accessible monotone glyphs in both Monaco themes", () => {
    type Theme = { rules?: Array<{ foreground?: string; fontStyle?: string }> };
    const definitions = new Map<string, Theme>();
    const monaco = {
      editor: {
        defineTheme(name: string, theme: Theme) {
          definitions.set(name, theme);
        },
      },
    } as unknown as typeof Monaco;

    registerSolarizedThemes(monaco);

    expect(definitions.get("vs-light")?.rules?.length).toBeGreaterThan(0);
    expect(definitions.get("vs-dark")?.rules?.length).toBeGreaterThan(0);
    const lightRules = definitions.get("vs-light")?.rules ?? [];
    const darkRules = definitions.get("vs-dark")?.rules ?? [];
    expect(lightRules.some((rule) => rule.foreground === "#859900")).toBe(true);
    expect(darkRules.some((rule) => rule.foreground === "#859900")).toBe(true);
    expect(lightRules.some((rule) => rule.fontStyle?.includes("underline"))).toBe(true);
    expect(darkRules.some((rule) => rule.fontStyle?.includes("underline"))).toBe(true);
  });

});
