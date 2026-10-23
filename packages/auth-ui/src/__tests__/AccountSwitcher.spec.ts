import { DOMWrapper, mount, flushPromises } from "@vue/test-utils";
import "./setup";
import { describe, it, expect, vi } from "vitest";
import { ref } from "vue";
import { DropdownMenu } from "@ulticode/design-system/ui/dropdown-menu";
import AccountSwitcher from "../components/AccountSwitcher.vue";

vi.mock("@ulticode/design-system/ui/sidebar", () => ({
  useSidebar: () => ({ isMobile: ref(false) }),
}));

const alice = {
  id: "alice",
  name: "Alice",
  email: "alice@example.test",
  role: "ADMIN",
  current: true,
};
const bob = {
  id: "bob",
  name: "Bob",
  email: "bob@example.test",
  role: "USER",
  current: false,
};

function client() {
  return {
    list: vi.fn().mockResolvedValue([alice, bob]),
    switchTo: vi.fn().mockResolvedValue(undefined),
    add: vi.fn(),
  };
}
function render(api = client(), adminOnly = false) {
  const wrapper = mount(AccountSwitcher, {
    attachTo: document.body,
    props: { client: api, adminOnly },
    slots: { trigger: "<button>Account</button>" },
  });
  return { wrapper, api, page: new DOMWrapper(document.body) };
}
async function open(wrapper: ReturnType<typeof render>["wrapper"]) {
  wrapper.findComponent(DropdownMenu).vm.$emit("update:open", true);
  await flushPromises();
  await vi.waitFor(() =>
    expect(document.querySelector('[role="menu"]')).not.toBeNull(),
  );
}

describe("AccountSwitcher", () => {
  it("loads real accounts when opened and marks the active account", async () => {
    const { wrapper, api, page } = render();
    try {
      await open(wrapper);
      expect(api.list).toHaveBeenCalledOnce();
      expect(page.text()).toContain("Alice");
      expect(page.text()).toContain("Bob");
      const active = page
        .findAll('[role="menuitem"]')
        .find((item) => item.text().includes("Alice"));
      expect(active?.attributes("data-disabled")).toBeDefined();
    } finally {
      wrapper.unmount();
    }
  });

  it("filters ordinary accounts from the administrator menu", async () => {
    const { wrapper, page } = render(client(), true);
    try {
      await open(wrapper);
      expect(page.text()).toContain("Alice");
      expect(page.text()).not.toContain("Bob");
    } finally {
      wrapper.unmount();
    }
  });

  it("keeps account addition available when no saved accounts exist", async () => {
    const api = client();
    api.list.mockResolvedValueOnce([]);
    const { wrapper, page } = render(api);
    try {
      await open(wrapper);
      const add = page
        .findAll('[role="menuitem"]')
        .find((item) => item.text().includes("auth.accounts.add"));
      expect(add?.attributes("data-disabled")).toBeUndefined();
      await add!.trigger("click");
      await flushPromises();
      expect(page.find('[role="dialog"]').exists()).toBe(true);
    } finally {
      wrapper.unmount();
    }
  });

  it("shows a recoverable error rather than pretending a failed request is an empty catalog", async () => {
    const api = client();
    api.list.mockRejectedValueOnce(new Error("offline"));
    const { wrapper, page } = render(api);
    try {
      await open(wrapper);
      expect(page.find('[role="alert"]').text()).toContain(
        "auth.accounts.failed",
      );
      await page.find('[role="alert"] button').trigger("click");
      await flushPromises();
      expect(api.list).toHaveBeenCalledTimes(2);
      expect(page.find('[role="alert"]').exists()).toBe(false);
      expect(page.text()).toContain("Alice");
    } finally {
      wrapper.unmount();
    }
  });

  it("keeps switching failures visible and never changes account locally", async () => {
    const api = client();
    api.switchTo.mockRejectedValueOnce(new Error("revoked"));
    const { wrapper, page } = render(api);
    try {
      await open(wrapper);
      const item = page
        .findAll('[role="menuitem"]')
        .find((item) => item.text().includes("Bob"));
      await item!.trigger("click");
      await flushPromises();
      expect(api.switchTo).toHaveBeenCalledWith("bob");
      expect(page.find('[role="alert"]').exists()).toBe(true);
    } finally {
      wrapper.unmount();
    }
  });
});
