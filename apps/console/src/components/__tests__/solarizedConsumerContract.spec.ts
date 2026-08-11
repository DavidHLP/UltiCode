import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

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
      "bg-status-warning-surface text-foreground-strong border border-[var(--status-warning)]",
    );
    expect(trigger).toContain(
      "data-[selected]:bg-primary data-[selected]:text-primary-foreground",
    );
    expect(trigger).toContain("data-[selected]:border-link-decoration");
    expect(trigger).toContain("bg-surface-highlight");
  });

  it("uses neutral card text with semantic value and trend markers", () => {
    const stats = source("../../components/dashboard/StatsCard.vue");

    expect(stats).toContain("text-foreground-strong decoration-status-success");
    expect(stats).toContain(
      "text-foreground-strong decoration-link-decoration",
    );
    expect(stats).toContain("text-foreground decoration-status-error");
  });

  it("keeps activity status text neutral and accents icons or underlines", () => {
    const activity = source("../../components/dashboard/RecentActivity.vue");

    expect(activity).toContain(
      'success: "text-foreground decoration-status-success',
    );
    expect(activity).toContain(
      'error: "text-foreground decoration-status-error',
    );
    expect(activity).toContain('electric: "text-[var(--accent-primary)]"');
    expect(activity).not.toContain("--solarized-");
  });

  it("uses semantic surfaces and readable text in achievement toasts", () => {
    const toast = source("../../components/achievement/UnlockToast.vue");

    expect(toast).toContain('"var(--status-warning)"');
    expect(toast).toContain('"var(--accent-primary)"');
    expect(toast).toContain("bg-status-warning-surface");
    expect(toast).toContain("text-muted-foreground");
    expect(toast).not.toMatch(
      /--solarized-|terminal-amber|text-gray|text-white/,
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
});
