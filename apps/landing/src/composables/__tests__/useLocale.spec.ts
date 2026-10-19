import { createApp, h } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import i18n from "@/i18n";
import zhCN from "@/i18n/locales/zh-CN/landing";
import enUS from "@/i18n/locales/en-US/landing";
import { useLocale } from "../useLocale";

afterEach(() => {
  vi.restoreAllMocks();
  window.localStorage.clear();
});

function keyShape(value: unknown): unknown {
  if (value === null || typeof value !== "object") return typeof value;
  return Object.fromEntries(
    Object.entries(value).map(([key, child]) => [key, keyShape(child)]),
  );
}

describe("useLocale", () => {
  it("keeps English and Chinese message keys aligned", () => {
    expect(keyShape(enUS)).toEqual(keyShape(zhCN));
  });

  it("switches language, updates document language, and persists preference", () => {
    let toggleLocale = () => {};
    const app = createApp({
      setup() {
        toggleLocale = useLocale().toggleLocale;
        return () => h("div");
      },
    });
    const root = document.createElement("div");
    app.use(i18n).mount(root);

    const current = i18n.global.locale.value;
    toggleLocale();
    const next = i18n.global.locale.value;
    expect(next).not.toBe(current);
    expect(document.documentElement.lang).toBe(next);
    expect(window.localStorage.getItem("ulticode-locale")).toBe(next);

    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("storage blocked");
    });
    expect(() => toggleLocale()).not.toThrow();
    expect(i18n.global.locale.value).toBe(current);
    expect(document.documentElement.lang).toBe(current);
    app.unmount();
  });
});
