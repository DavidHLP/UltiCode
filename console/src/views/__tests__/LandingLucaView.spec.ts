import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import LandingLucaView from "../LandingLucaView.vue";

const push = vi.fn();

vi.mock("vue-router", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ isAuthenticated: false }),
}));

vi.mock("vue-i18n", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

const routeHref = (to: { name?: string; params?: Record<string, string> }) => {
  const params = to.params ? "/" + Object.values(to.params).join("/") : "";
  return `/${to.name ?? ""}${params}`;
};

const mountView = () =>
  mount(LandingLucaView, {
    global: {
      stubs: {
        RouterLink: {
          props: ["to"],
          template: '<a :href="routeHref(to)"><slot /></a>',
          methods: { routeHref },
        },
      },
    },
  });

describe("LandingLucaView", () => {
  beforeEach(() => {
    push.mockClear();
    window.sessionStorage.clear();
    try {
      window.sessionStorage.removeItem("luca-entered");
    } catch {
      /* ignore */
    }
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders the four brand word-art words", () => {
    const wrapper = mountView();
    const words = wrapper.findAll(".luca-word-text").map((w) => w.text());

    expect(words).toEqual([
      "landingLuca.hero.words.code",
      "landingLuca.hero.words.judge",
      "landingLuca.hero.words.compete",
      "landingLuca.hero.words.learn",
    ]);
  });

  it("shows the loading portal counter and ENTER control", () => {
    const wrapper = mountView();
    const counter = wrapper.find(".luca-portal-counter");

    expect(counter.text()).toBe("000 / 024");
    expect(wrapper.find(".luca-portal-enter").exists()).toBe(true);
  });

  it("reveals ENTER and fills the counter after skipping", async () => {
    const wrapper = mountView();
    const enter = wrapper.find(".luca-portal-enter");

    expect(enter.attributes("tabindex")).toBe("-1");

    await wrapper.find(".luca-portal-skip").trigger("click");
    expect(wrapper.find(".luca-portal-counter").text()).toBe("024 / 024");
    expect(wrapper.find(".luca-portal-enter").attributes("tabindex")).toBe("0");
  });

  it("marks the portal as leaving once ENTER is activated", async () => {
    const wrapper = mountView();
    await wrapper.find(".luca-portal-skip").trigger("click");
    await wrapper.find(".luca-portal-enter").trigger("click");

    expect(wrapper.find(".luca-portal.is-leaving").exists()).toBe(true);
  });

  it("routes the hero CTA straight into the seeded problem", async () => {
    const wrapper = mountView();
    await wrapper.find(".luca-hero-cta").trigger("click");

    expect(push).toHaveBeenCalledWith({
      name: "problem-detail",
      params: { slug: "two-sum" },
    });
  });

  it("routes the contact CTA straight into the seeded problem", async () => {
    const wrapper = mountView();
    await wrapper.find(".luca-contact-cta").trigger("click");

    expect(push).toHaveBeenCalledWith({
      name: "problem-detail",
      params: { slug: "two-sum" },
    });
  });

  it("routes the nav talk button into the seeded problem", async () => {
    const wrapper = mountView();
    await wrapper.find(".luca-talk").trigger("click");

    expect(push).toHaveBeenCalledWith({
      name: "problem-detail",
      params: { slug: "two-sum" },
    });
  });

  it("opens the mobile menu with the four primary destinations", async () => {
    const wrapper = mountView();
    expect(wrapper.find(".luca-menu").exists()).toBe(false);

    await wrapper.find(".luca-burger").trigger("click");
    const menuLinks = wrapper.findAll(".luca-menu-link");

    expect(menuLinks).toHaveLength(4);
  });

  it("exposes a skip link to main content for keyboard users", () => {
    const wrapper = mountView();

    expect(wrapper.find(".luca-skip").attributes("href")).toBe("#luca-main");
    expect(wrapper.find("#luca-main").exists()).toBe(true);
  });

  it("links every work card to a real product surface", () => {
    const wrapper = mountView();
    const hrefs = wrapper.findAll(".luca-work-card").map((c) => c.attributes("href"));

    expect(hrefs).toEqual([
      "/problem-detail/two-sum",
      "/problemset",
      "/problemset",
      "/contest-list",
    ]);
  });

  it("numbers each work card with a three-digit index", () => {
    const wrapper = mountView();
    const indexes = wrapper.findAll(".luca-work-index").map((el) => el.text());

    expect(indexes).toEqual(["001", "002", "003", "004"]);
  });

  it("renders the awards marquee with a localized set of badges", () => {
    const wrapper = mountView();
    const awards = wrapper.findAll(".luca-award-label");

    expect(awards.length).toBeGreaterThanOrEqual(6);
    expect(wrapper.find(".luca-awards-track").exists()).toBe(true);
  });

  it("renders a scroll progress indicator", () => {
    const wrapper = mountView();
    expect(wrapper.find(".luca-progress").exists()).toBe(true);
  });

  it("renders the problem section eyebrow and title", () => {
    const wrapper = mountView();
    expect(wrapper.find(".luca-problem .luca-eyebrow").text()).toBe("landingLuca.problem.eyebrow");
    expect(wrapper.find(".luca-problem .luca-section-title").exists()).toBe(true);
  });

  it("renders three solution orbit labels", () => {
    const wrapper = mountView();
    expect(wrapper.findAll(".luca-solution-orbit")).toHaveLength(3);
  });

  it("renders the five-step experience flow in order", () => {
    const wrapper = mountView();
    expect(wrapper.findAll(".luca-flow-step")).toHaveLength(5);
    expect(wrapper.findAll(".luca-flow-index").map((e) => e.text())).toEqual(["01", "02", "03", "04", "05"]);
  });

  it("renders the about statement", () => {
    const wrapper = mountView();
    expect(wrapper.find(".luca-about-statement").exists()).toBe(true);
  });

  it("does not pollute pinned landing selectors", () => {
    const wrapper = mountView();
    expect(wrapper.findAll(".luca-work-card")).toHaveLength(4);
    expect(wrapper.findAll(".luca-word-text")).toHaveLength(4);
    expect(wrapper.findAll(".luca-work-index")).toHaveLength(4);
  });

  it("mounts the world scene as a page-wide layer outside the hero section", () => {
    const wrapper = mountView();
    const scene = wrapper.find(".luca-hero-scene");
    expect(scene.exists()).toBe(true);
    // The scrollytelling world canvas is a sibling of <main>, not clipped to
    // .luca-hero — and the hero CTA still lives inside the hero section.
    expect(scene.element.closest(".luca-hero")).toBeNull();
    expect(scene.element.closest(".luca-root")).not.toBeNull();
    expect(wrapper.find(".luca-hero .luca-hero-cta").exists()).toBe(true);
  });

  it("marks the eight narrative beats for scroll pinning", () => {
    const wrapper = mountView();
    // Hero and Contact are intentionally not beats; the eight middle narrative
    // sections carry .luca-beat so useLucaBeat can pin each while the world
    // canvas plays its matching 3D beat.
    expect(wrapper.findAll(".luca-beat")).toHaveLength(8);
    expect(wrapper.find(".luca-hero.luca-beat").exists()).toBe(false);
    expect(wrapper.find(".luca-contact.luca-beat").exists()).toBe(false);
  });
});
