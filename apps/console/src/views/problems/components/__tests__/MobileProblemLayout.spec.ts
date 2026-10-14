import { describe, expect, it, vi } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import MobileProblemLayout from "../MobileProblemLayout.vue";

vi.mock("vue-router", () => ({
  useRoute: () => ({
    name: "problem-detail",
    params: { tab: "code" },
    query: {},
  }),
  useRouter: () => ({ push: vi.fn().mockResolvedValue(undefined) }),
}));
vi.mock("vue-i18n", () => ({ useI18n: () => ({ t: (key: string) => key }) }));
vi.mock("../../useProblemContext", () => ({
  useProblemContext: () => ({ contestId: { value: null } }),
}));

describe("MobileProblemLayout selected tab label", () => {
  it("shows the route-selected Code label before the select popup mounts", async () => {
    const wrapper = mount(MobileProblemLayout);
    await flushPromises();
    expect(wrapper.get('[data-slot="select-trigger"]').text()).toBe(
      "problem.layout.code",
    );
    expect(wrapper.get('[data-slot="select-trigger"]').text()).not.toContain(
      "common.labels.selectTab",
    );
    wrapper.unmount();
  });
});
