import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import { RouterLinkStub } from "@vue/test-utils";
import LandingChrome from "../components/LandingChrome.vue";

vi.mock("vue-i18n", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => key }),
}));

const authState = { isAuthenticated: false };

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => authState,
}));

vi.mock("@/components/ThemeSwitcher.vue", () => ({
  default: { template: "<div />" },
}));

vi.mock("@/components/LanguageSwitcher.vue", () => ({
  default: { template: "<div />" },
}));

function mountChrome() {
  return mount(LandingChrome, {
    global: {
      stubs: {
        RouterLink: RouterLinkStub,
      },
    },
  });
}

describe("LandingChrome", () => {
  it("shows the primary CTA and a login entry to guests", () => {
    authState.isAuthenticated = false;
    const wrapper = mountChrome();
    const text = wrapper.text();
    expect(text).toContain("landing.hero.ctaPrimary");
    expect(text).toContain("landing.nav.login");
    expect(text).not.toContain("landing.nav.enter");
  });

  it("shows a platform entry to signed-in users instead of auth links", () => {
    authState.isAuthenticated = true;
    const wrapper = mountChrome();
    const text = wrapper.text();
    expect(text).toContain("landing.nav.enter");
    expect(text).not.toContain("landing.nav.login");
  });

  it("links to the real product surfaces", () => {
    authState.isAuthenticated = false;
    const wrapper = mountChrome();
    const names = wrapper
      .findAllComponents(RouterLinkStub)
      .map((link) => (link.props("to") as { name?: string })?.name);
    expect(names).toEqual(
      expect.arrayContaining([
        "problemset",
        "contest-home",
        "forum-home",
        "contest-rankings",
      ]),
    );
  });
});
