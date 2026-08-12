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
    expect(trigger).toContain("data-[selected]:border-primary");
    expect(trigger).toContain("border-control");
    expect(trigger).toContain("bg-surface-highlight");
  });

  it("keeps info banners on the cyan status contract", () => {
    const banners = source("../../components/problem/FeaturedBanners.vue");

    expect(banners).toContain(
      'icon: "text-status-info-mark bg-surface-highlight border-border-control"',
    );
    expect(banners).toContain('card: "hover:border-border-control"');
    expect(banners).not.toContain("border-l-4");
    expect(banners).not.toContain("bg-status-info-surface");
  });

  it("uses the warning marker for unread notifications", () => {
    const notification = source(
      "../../components/notification/NotificationBadge.vue",
    );
    const notificationView = source("../../views/personal/NotificationsView.vue");
    const navUser = source("../../features/sider/NavUser.vue");

    expect(notification).toContain("bg-status-warning-mark");
    expect(notification).toContain("bg-status-warning-surface");
    expect(notification).toContain('variant="outline"');
    expect(notification).not.toContain("rounded-full bg-primary");
    expect(notificationView).toContain(
      "border-status-warning-mark/30 bg-status-warning-surface",
    );
    expect(notificationView).toContain("text-status-warning-mark");
    expect(notificationView).not.toContain("bg-primary/5");
    expect(navUser).toContain(
      "bg-status-warning-surface text-foreground-strong border-status-warning-mark",
    );
    expect(navUser).not.toContain('variant="destructive"');
  });

  it("uses one shared row contract across problem sidebar modules", () => {
    const nav = source("../../features/sider/SidebarNav.vue");
    const actions = source("../../features/sider/Calendars.vue");
    const lists = source(
      "../../features/sider/components/SidebarListSections.vue",
    );
    const sharedStyles = source(
      "../../../../../packages/sidebar-menu/src/styles/sidebar-menu.css",
    );

    expect(nav).toContain("SharedSidebarMenuItem");
    expect(nav).toContain('class="uc-sidebar-item group"');
    expect(nav).not.toContain("border-l-4");
    expect(actions).toContain("SidebarMenuItem as SharedSidebarMenuItem");
    expect(actions).toContain('as="button"');
    expect(actions).not.toContain("border-dashed");
    expect(lists).toContain("uc-sidebar-item");
    expect(lists).toContain("data-active");
    expect(lists).not.toContain("border-l-2");
    expect(lists).not.toContain("hover:bg-[var(--primary)]/4");
    expect(sharedStyles).toContain(".uc-sidebar-item:focus-visible");
    expect(sharedStyles).not.toContain(".uc-sidebar-sub-item {\n  display: flex");
  });

  it("keeps the sticky app header above scrolling feature content", () => {
    const layout = source("../../features/sider/AppLayout.vue");

    expect(layout).toContain(
      "sticky top-0 z-30 flex h-14 shrink-0 items-center",
    );
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

  it("keeps electric data emphasis on the explicit accent token", () => {
    const badge = source(
      "../../../../../packages/badge-config/src/useSemanticBadge.ts",
    );
    const contest = source("../../types/contest.ts");

    expect(badge).toContain("electric: 'bg-[var(--accent-primary)]'");
    expect(contest).toContain('EXPERT: "var(--accent-primary)"');
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

  it("uses the public surface and geometry contract for dialogs", () => {
    const dialog = source("../../components/ui/dialog/DialogContent.vue");
    const input = source("../../components/ui/input/Input.vue");
    const textarea = source("../../components/ui/textarea/Textarea.vue");
    const switchSource = source("../../components/ui/switch/Switch.vue");
    const button = source("../../components/ui/button/index.ts");

    expect(dialog).toContain("bg-surface-elevated");
    expect(dialog).toContain("rounded-xl");
    expect(dialog).toContain("shadow-float");
    expect(input).toContain("bg-surface-sunken");
    expect(input).toContain("rounded-md");
    expect(input).toContain("defineExpose({ focus:");
    expect(input).toContain("focus-visible:aria-invalid:ring-destructive");
    expect(input).toContain(
      "dark:focus-visible:aria-invalid:ring-destructive",
    );
    expect(textarea).toContain("bg-surface-sunken");
    expect(textarea).toContain("rounded-md");
    expect(textarea).toContain("focus-visible:aria-invalid:ring-destructive");
    expect(textarea).toContain(
      "dark:focus-visible:aria-invalid:ring-destructive",
    );
    expect(switchSource).toContain(
      "data-[state=unchecked]:bg-surface-highlight",
    );
    expect(button).toContain("rounded-md");
    expect(button).toContain("focus-visible:aria-invalid:ring-destructive");
    expect(button).toContain(
      "dark:focus-visible:aria-invalid:ring-destructive",
    );
  });

  it("keeps the header interaction rail on shared surfaces", () => {
    const layout = source("../../features/sider/AppLayout.vue");
    const navigation = source(
      "../../components/ui/navigation-menu/NavigationMenuLink.vue",
    );
    const popover = source(
      "../../components/ui/popover/PopoverContent.vue",
    );
    const search = source("../../components/search/GlobalSearch.vue");
    const variants = source("../../../../../packages/design-system/src/variants.ts");

    expect(layout).toContain("bg-surface-sunken");
    expect(layout).toContain("border-border-subtle");
    expect(navigation).toContain("bg-surface-highlight");
    expect(navigation).toContain("focus-visible:ring-2");
    expect(popover).toContain("rounded-lg");
    expect(popover).toContain("shadow-float");
    expect(search).toContain("bg-surface-highlight");
    expect(search).not.toContain("bg-accent");
    expect(variants).not.toContain("dark:bg-input");
    expect(variants).not.toContain("hover:bg-accent");
  });

  it("uses semantic locale markers instead of emoji flags", () => {
    const switcher = source("../../components/LanguageSwitcher.vue");
    const localeTypes = source("../../i18n/types.ts");

    expect(switcher).toContain('localeConfig.code.split("-")[0].toUpperCase()');
    expect(switcher).toContain("bg-surface-sunken");
    expect(switcher).toContain("border-border-control");
    expect(switcher).toContain("shadow-float");
    expect(switcher).not.toContain("localeConfig.flag");
    expect(switcher).not.toContain("bg-accent");
    expect(localeTypes).not.toContain("flag:");
  });

  it("keeps each top-level navigation item active across nested routes", () => {
    const layout = source("../../features/sider/AppLayout.vue");

    expect(layout).toContain('activePath: "/problemset"');
    expect(layout).toContain('activePath: "/forum"');
    expect(layout).toContain('activePath: "/contest"');
    expect(layout).toContain("route.path === item.activePath");
    expect(layout).toContain("route.path.startsWith(`${item.activePath}/`)");
  });

  it("uses accessible monotone glyphs in both Monaco themes", () => {
    const monacoSource = source("../../utils/monaco-solarized-theme.ts");
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
    expect(monacoSource).toContain("focusBorder: SOLARIZED.base00");
    expect(monacoSource).toContain("focusBorder: SOLARIZED.base0");
  });

  it("keeps active document navigation on the primary monotone marker", () => {
    const forum = source("../../views/forum/ForumThreadView.vue");
    const solution = source(
      "../../views/problems/solutions/components/SolutionDetail.vue",
    );

    expect(forum).toContain("border-primary text-[var(--primary)]");
    expect(solution).toContain("border-primary text-[var(--primary)]");
  });

});
