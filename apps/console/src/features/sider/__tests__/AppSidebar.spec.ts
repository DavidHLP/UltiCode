import { flushPromises, mount } from "@vue/test-utils";
import { markRaw, nextTick, reactive, ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import AppSidebar from "../AppSidebar.vue";
import SidebarNav from "../SidebarNav.vue";
import { fetchForumCommunities } from "@/api/forum";

const settingsOpen = ref(false);
const sidebarState = ref("expanded");
const isMobile = ref(false);
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
vi.mock("@ulticode/design-system/ui/sidebar", async () => {
  const { Primitive } = await import("reka-ui");
  const component = { template: "<div><slot /></div>" };
  return {
    useSidebar: () => ({
      setOpenMobile: closeMobile,
      state: sidebarState,
      isMobile,
      setOpen: (value: boolean) => {
        sidebarState.value = value ? "expanded" : "collapsed";
      },
    }),
    SidebarGroup: component,
    SidebarMenu: component,
    SidebarMenuButton: {
      components: { Primitive },
      props: ["isActive", "asChild", "tooltip"],
      template:
        '<Primitive as="button" :as-child="asChild" :data-active="isActive || undefined"><slot /></Primitive>',
    },
    SidebarMenuItem: component,
    SidebarMenuSub: component,
    SidebarMenuSubItem: component,
    SidebarMenuSubButton: {
      components: { Primitive },
      props: ["isActive", "asChild"],
      template:
        '<Primitive as="button" :as-child="asChild" :data-active="isActive || undefined"><slot /></Primitive>',
    },
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
    isMobile.value = false;
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
          wrapper.findAll('[data-active="true"]').map((node) => node.text()),
        ).toEqual([expected]);
      } finally {
        wrapper.unmount();
      }
    },
  );

  it.each([
    ["/forum/c", "/forum/c", "Community"],
    ["/forum/c", "/forum/c/general", "General"],
    [undefined, "/forum/c/general", "General"],
  ])(
    "selects only the destination and keeps its directory neutral (%s, %s)",
    (url, path, expected) => {
      route.path = path;
      const wrapper = mount(SidebarNav, {
        props: {
          sections: [
            {
              name: "Categories",
              items: [
                {
                  title: "Community",
                  url,
                  children: [{ title: "General", url: "/forum/c/general" }],
                },
              ],
            },
          ],
        },
        global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
      });
      try {
        expect(
          wrapper.findAll('[data-active="true"]').map((node) => node.text()),
        ).toEqual([expected]);
        expect(
          wrapper.get('button[aria-label="Categories"]').attributes("data-active"),
        ).toBeUndefined();
      } finally {
        wrapper.unmount();
      }
    },
  );

  it("opens a collapsed branch without replacing its focused trigger", async () => {
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
      sidebarState.value = "collapsed";
      await nextTick();
      const trigger = wrapper.get('button[aria-label="Categories"]');
      (trigger.element as HTMLButtonElement).focus();
      expect(wrapper.findAll("a")).toHaveLength(0);
      await trigger.trigger("click");
      await nextTick();
      expect(sidebarState.value).toBe("expanded");
      expect(wrapper.get("a").text()).toBe("General");
      expect(document.activeElement).toBe(trigger.element);
      await trigger.trigger("click");
      expect(trigger.attributes("aria-expanded")).toBe("false");
    } finally {
      wrapper.unmount();
    }
  });

  it("keeps branch children usable in the mobile drawer when desktop is collapsed", async () => {
    sidebarState.value = "collapsed";
    isMobile.value = true;
    const wrapper = mount(SidebarNav, {
      props: {
        sections: [
          {
            name: "Categories",
            items: [{ title: "General", url: "/forum/c/general" }],
          },
        ],
      },
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    });
    try {
      expect(wrapper.get("a").text()).toBe("General");
      await wrapper.get('button[aria-label="Categories"]').trigger("click");
      expect(
        wrapper
          .get('button[aria-label="Categories"]')
          .attributes("aria-expanded"),
      ).toBe("false");
      expect(sidebarState.value).toBe("collapsed");
    } finally {
      wrapper.unmount();
    }
  });

  it.each([false, true])(
    "highlights the destination icon without highlighting its directory (collapsible: %s)",
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
            .findAll(
              state === "expanded"
                ? 'a [data-test="nav-icon"]'
                : 'button[aria-label="Navigation"] [data-test="nav-icon"]',
            )
            .find((node) => node.isVisible());
          expect(icon?.classes()).toContain(
            state === "expanded" ? "text-inherit" : "text-muted-foreground",
          );
          expect(icon?.classes()).not.toContain("text-primary");
          expect(wrapper.findAll('button[data-active="true"]')).toHaveLength(0);
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
