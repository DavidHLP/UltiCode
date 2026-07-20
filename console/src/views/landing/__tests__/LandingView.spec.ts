import { mount } from "@vue/test-utils";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { createRouter, createMemoryHistory } from "vue-router";
import { i18n } from "@/i18n";
import LandingView from "../LandingView.vue";
import appRouter from "@/router";

// Switchers pull virtual icon modules (~icons/*) that the vitest pipeline
// does not resolve; they are covered by their own surface.
vi.mock("@/components/ThemeSwitcher.vue", () => ({
  default: { template: "<div />" },
}));
vi.mock("@/components/LanguageSwitcher.vue", () => ({
  default: { template: "<div />" },
}));

const Dummy = { template: "<div />" };

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", name: "landing", component: Dummy },
      { path: "/problemset", name: "problemset", component: Dummy },
      { path: "/contest", name: "contest-home", component: Dummy },
      { path: "/forum", name: "forum-home", component: Dummy },
      {
        path: "/contest/rankings",
        name: "contest-rankings",
        component: Dummy,
      },
      { path: "/login", name: "login", component: Dummy },
      { path: "/register", name: "register", component: Dummy },
      { path: "/personal", name: "personal-profile", component: Dummy },
    ],
  });
}

async function mountLanding() {
  const router = createTestRouter();
  await router.push("/");
  const wrapper = mount(LandingView, {
    global: {
      plugins: [router, i18n],
    },
  });
  // LandingCanvas dynamically imports the three.js scene, which cannot
  // create a WebGL context under jsdom — wait for the failure to settle.
  await vi.waitFor(
    () => {
      expect(
        wrapper.find(".landing-canvas-fallback").exists() ||
          wrapper.find("canvas.is-ready").exists(),
      ).toBe(true);
    },
    { timeout: 3000, interval: 20 },
  );
  return wrapper;
}

describe("LandingView", () => {
  beforeAll(() => {
    setActivePinia(createPinia());
    // jsdom reports en-US via the locale detector; pin zh-CN so the copy
    // assertions below exercise the primary locale deterministically.
    i18n.global.locale.value = "zh-CN";
  });

  it("renders the hero value proposition and primary CTAs as DOM links", async () => {
    const wrapper = await mountLanding();
    expect(wrapper.text()).toContain("让每一次提交，都成为可见的进步。");

    const links = wrapper.findAll("a");
    const hrefs = links.map((link) => link.attributes("href"));
    expect(hrefs).toContain("/problemset");
    expect(hrefs).toContain("/contest");
    expect(hrefs).toContain("/login");
    expect(hrefs).toContain("/register");
  });

  it("renders every narrative chapter with real localized copy", async () => {
    const wrapper = await mountLanding();
    const text = wrapper.text();
    expect(text).toContain("代码落下，结构浮现。");
    expect(text).toContain("穿过测试矩阵，立刻知道结果。");
    expect(text).toContain("提交不再是孤立事件。");
    expect(text).toContain("从个人轨迹，到更大的星图。");
    expect(text).toContain("下一次提交，从这里开始。");
    // The judge demo is labelled as illustrative, never as live data.
    expect(text).toContain("示意演示");
  });

  it("falls back to the static core when WebGL is unavailable", async () => {
    const wrapper = await mountLanding();
    // jsdom has no WebGL — the canvas layer must degrade, and the page
    // content must stay complete and navigable.
    expect(wrapper.find(".landing-canvas-fallback").exists()).toBe(true);
    expect(wrapper.find("main").exists()).toBe(true);
  });
});

describe("app router", () => {
  it("registers the public landing route at /", () => {
    expect(appRouter.hasRoute("landing")).toBe(true);
    const record = appRouter.getRoutes().find((r) => r.name === "landing");
    expect(record?.path).toBe("/");
    expect(record?.meta?.requiresAuth).toBeUndefined();
  });
});
