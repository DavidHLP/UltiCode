import { flushPromises, mount } from "@vue/test-utils";
import { markRaw, nextTick, reactive, ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import AppSidebar from "../AppSidebar.vue";
import SidebarNav from "../SidebarNav.vue";
import { fetchForumCommunities } from "@/api/forum";

const settingsOpen = ref(false);
const sidebarState = ref("expanded");
const route = reactive<{ path: string; query: Record<string, string> }>({
  path: "/problemset",
  query: {},
});
const { closeMobile, removeHook, afterEach } = vi.hoisted(() => ({
  closeMobile: vi.fn(),
  removeHook: vi.fn(),
  afterEach: vi.fn(),
}));

vi.mock("@ulticode/theme", () => ({
  useAppearance: () => ({ settingsOpen }),
}));
vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ isAuthenticated: false, user: null }),
}));
vi.mock("@/api/forum", () => ({ fetchForumCommunities: vi.fn() }));
vi.mock("vue-i18n", async (original) => ({
  ...(await original<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => key }),
}));
vi.mock("vue-router", async (original) => ({
  ...(await original<typeof import("vue-router")>()),
  useRoute: () => route,
  useRouter: () => ({ afterEach }),
}));
vi.mock("@ulticode/design-system/ui/sidebar", () => {
  const component = { template: "<div><slot /></div>" };
  return {
    useSidebar: () => ({ setOpenMobile: closeMobile, state: sidebarState }),
    SidebarGroup: component,
    SidebarMenu: component,
    SidebarMenuButton: component,
    SidebarMenuItem: component,
    SidebarMenuSub: component,
    Sidebar: component,
    SidebarContent: component,
    SidebarFooter: component,
    SidebarHeader: component,
    SidebarRail: component,
    SidebarSeparator: component,
  };
});

function mountSidebar() {
  return mount(AppSidebar, {
    global: { stubs: { Calendars: true, NavUser: true, SidebarNav: true } },
  });
}

describe("AppSidebar mobile overlays", () => {
  beforeEach(() => {
    settingsOpen.value = false;
    sidebarState.value = "expanded";
    route.path = "/problemset";
    route.query = {};
    vi.clearAllMocks();
    vi.mocked(fetchForumCommunities).mockResolvedValue([]);
    afterEach.mockReturnValue(removeHook);
  });

  it.each([
    ["/contest/browse/past", "", "Past"],
    ["/contest/rankings", "", "Global"],
    ["/contest/rankings", "local", "Local"],
    ["/contest/rankings", "unknown", "Global"],
  ])(
    "selects the canonical contest destination (%s, %s)",
    async (path, scope, expected) => {
      route.path = path;
      route.query = scope ? { scope } : {};
      const wrapper = mount(SidebarNav, {
        props: {
          sections: [
            {
              name: "Contests",
              items: [
                { title: "Past", url: "/contest/past" },
                { title: "Global", url: "/contest/global-ranking" },
                { title: "Local", url: "/contest/local-ranking" },
              ],
            },
          ],
        },
        global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
      });
      try {
        expect(
          wrapper.findAll('a[data-active="true"]').map((node) => node.text()),
        ).toEqual([expected]);
      } finally {
        wrapper.unmount();
      }
    },
  );

  it("keeps collapsed group links available without clipped headings", async () => {
    const wrapper = mount(SidebarNav, {
      attachTo: document.body,
      props: {
        sections: [
          {
            name: "Categories",
            collapsible: true,
            items: [{ title: "General", url: "/forum/c/general" }],
          },
        ],
      },
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    });
    try {
      await wrapper.get(".uc-sidebar-section-trigger").trigger("click");
      sidebarState.value = "collapsed";
      await nextTick();
      expect(wrapper.get(".uc-sidebar-section-trigger").isVisible()).toBe(
        false,
      );
      expect(
        wrapper
          .findAll("a")
          .some((link) => link.isVisible() && link.text() === "General"),
      ).toBe(true);
      sidebarState.value = "expanded";
      await nextTick();
      expect(wrapper.get(".uc-sidebar-section-trigger").isVisible()).toBe(true);
      expect(
        wrapper.get(".uc-sidebar-section-trigger").attributes("data-state"),
      ).toBe("closed");
    } finally {
      wrapper.unmount();
    }
  });

  it.each([false, true])(
    "inherits the active row foreground for icons (collapsible: %s)",
    async (collapsible) => {
      const wrapper = mount(SidebarNav, {
        props: {
          sections: [
            {
              name: "Navigation",
              collapsible,
              items: [
                {
                  title: "Problems",
                  url: "/problemset",
                  icon: markRaw({ template: '<svg data-test="nav-icon" />' }),
                },
              ],
            },
          ],
        },
        global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
      });
      try {
        for (const state of ["expanded", "collapsed"]) {
          sidebarState.value = state;
          await nextTick();
          const icon = wrapper
            .findAll('[data-test="nav-icon"]')
            .find((node) => node.isVisible());
          expect(icon?.classes()).toContain("text-inherit");
          expect(icon?.classes()).not.toContain("text-primary");
        }
        route.path = "/forum";
        await nextTick();
        const icon = wrapper
          .findAll('[data-test="nav-icon"]')
          .find((node) => node.isVisible());
        expect(icon?.classes()).toContain("text-muted-foreground");
      } finally {
        wrapper.unmount();
      }
    },
  );

  it("builds forum category links from real communities", async () => {
    route.path = "/forum/c/general";
    vi.mocked(fetchForumCommunities).mockResolvedValue([
      {
        id: "community-general",
        name: "General",
        slug: "general",
        description: "",
        members: 0,
        online: 0,
      },
    ]);
    const wrapper = mountSidebar();
    try {
      await flushPromises();
      const sections = wrapper
        .findComponent({ name: "SidebarNav" })
        .props("sections");
      expect(
        sections.find(
          (section: { name: string }) =>
            section.name === "sidebar.forum.categories",
        ).items,
      ).toEqual([
        expect.objectContaining({ label: "General", url: "/forum/c/general" }),
      ]);
    } finally {
      wrapper.unmount();
    }
  });

  it("shows a retry when forum categories fail to load", async () => {
    route.path = "/forum";
    vi.mocked(fetchForumCommunities).mockRejectedValueOnce(
      new Error("unavailable"),
    );
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    const wrapper = mountSidebar();
    try {
      await flushPromises();
      expect(wrapper.text()).toContain("forum.list.communitiesLoadFailed");
      await wrapper.get("button").trigger("click");
      await flushPromises();
      expect(fetchForumCommunities).toHaveBeenCalledTimes(2);
      expect(wrapper.text()).not.toContain("forum.list.communitiesLoadFailed");
    } finally {
      wrapper.unmount();
      errorSpy.mockRestore();
    }
  });

  it("closes the drawer when appearance opens and stops watching on unmount", async () => {
    const wrapper = mountSidebar();
    settingsOpen.value = true;
    await nextTick();
    expect(closeMobile).toHaveBeenCalledWith(false);
    closeMobile.mockClear();
    settingsOpen.value = false;
    await nextTick();
    expect(closeMobile).not.toHaveBeenCalled();
    wrapper.unmount();
    settingsOpen.value = true;
    await nextTick();
    expect(closeMobile).not.toHaveBeenCalled();
    expect(removeHook).toHaveBeenCalledOnce();
  });

  it("closes on successful or duplicate navigation but preserves aborted navigation", async () => {
    const wrapper = mountSidebar();
    const hook = afterEach.mock.calls[0]![0];
    hook({}, {}, undefined);
    expect(closeMobile).toHaveBeenCalledWith(false);
    closeMobile.mockClear();
    const router = createRouter({
      history: createMemoryHistory(),
      routes: ["/one", "/two"].map((path) => ({
        path,
        component: { template: "<div />" },
      })),
    });
    await router.push("/one");
    hook({}, {}, await router.push("/one"));
    expect(closeMobile).toHaveBeenCalledWith(false);
    closeMobile.mockClear();
    router.beforeEach(() => false);
    hook({}, {}, await router.push("/two"));
    expect(closeMobile).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});
