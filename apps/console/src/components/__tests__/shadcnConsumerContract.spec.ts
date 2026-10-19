import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import type * as Monaco from "monaco-editor";
import { UI_PALETTE } from "@ulticode/design-system";
import { registerAppThemes } from "../../utils/monaco-theme";

const app = (file: string) =>
  readFileSync(resolve(process.cwd(), "src", file), "utf8");
const ui = (file: string) =>
  readFileSync(
    resolve(
      process.cwd(),
      "../../packages/design-system/src/components/ui",
      file,
    ),
    "utf8",
  );

describe("official shared component consumers", () => {
  it("has one UI owner instead of app-local copies", () => {
    expect(existsSync(resolve(process.cwd(), "src/components/ui"))).toBe(false);
    for (const file of [
      "LanguageSwitcher.vue",
      "ThemeSwitcher.vue",
      "ReportDialog.vue",
    ]) {
      expect(app("components/" + file)).toContain(
        "@ulticode/design-system/ui/",
      );
    }
  });

  it("uses official selected calendar colors and paired switch colors", () => {
    const calendar = ui("calendar/CalendarCellTrigger.vue");
    expect(calendar).toContain("data-[selected]:bg-primary");
    expect(calendar).toContain("data-[selected]:text-primary-foreground");
    const switchSource = ui("switch/Switch.vue");
    expect(switchSource).toContain("data-checked:bg-primary");
    expect(switchSource).toContain("dark:data-checked:bg-primary-foreground");
  });

  it("uses official dialog, input and textarea accessibility and surfaces", () => {
    expect(ui("dialog/DialogContent.vue")).toContain("bg-popover");
    expect(ui("dialog/DialogContent.vue")).toContain("DialogContent");
    for (const control of ["input/Input.vue", "textarea/Textarea.vue"]) {
      expect(ui(control)).toContain("aria-invalid");
      expect(ui(control)).toContain("focus-visible:ring-ring");
      expect(ui(control)).toContain("border-input");
    }
    expect(ui("input/Input.vue")).toContain("defineExpose({ focus:");
  });

  it("keeps theme and appearance settings available to guests", () => {
    const nav = app("features/sider/NavUser.vue");
    const guest = nav.slice(
      nav.indexOf("<!-- Guest: unified dropdown -->"),
      nav.indexOf("<!-- Authenticated: show user dropdown menu -->"),
    );
    expect(guest).toContain("<ThemeSwitcher />");
    expect(app("components/ThemeSwitcher.vue")).toContain(
      "openAppearanceSettings",
    );
  });

  it("keeps locale markers textual and top-level navigation active for nested routes", () => {
    const language = app("components/LanguageSwitcher.vue");
    expect(language).toContain('localeConfig.code.split("-")[0].toUpperCase()');
    expect(language).not.toContain("localeConfig.flag");
    const layout = app("features/sider/AppLayout.vue");
    for (const path of ["/problemset", "/forum", "/contest"])
      expect(layout).toContain(`activePath: "${path}"`);
    expect(layout).toContain("route.path.startsWith(`${item.activePath}/`)");
  });

  it("retains syntax semantics with non-color cues in both Monaco modes", () => {
    type Theme = { rules?: Array<{ foreground?: string; fontStyle?: string }> };
    const definitions = new Map<string, Theme>();
    const monaco = {
      editor: {
        defineTheme(name: string, theme: Theme) {
          definitions.set(name, theme);
        },
      },
    } as unknown as typeof Monaco;
    registerAppThemes(monaco);
    for (const name of ["vs-light", "vs-dark"]) {
      const rules = definitions.get(name)?.rules ?? [];
      expect(
        rules.some(
          (rule) =>
            rule.foreground ===
            (name === "vs-dark" ? UI_PALETTE.darkGreen : UI_PALETTE.green),
        ),
      ).toBe(true);
      expect(rules.some((rule) => rule.fontStyle?.includes("underline"))).toBe(
        true,
      );
      expect(rules.some((rule) => rule.fontStyle?.includes("bold"))).toBe(true);
    }
    expect(definitions.has("hc-black")).toBe(false);
  });
});
