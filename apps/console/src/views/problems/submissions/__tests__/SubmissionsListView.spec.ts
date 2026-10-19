import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import SubmissionsListView from "../SubmissionsListView.vue";

vi.mock("vue-i18n", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

describe("SubmissionsListView empty state", () => {
  it("shows empty guidance once inside the table", () => {
    const wrapper = mount(SubmissionsListView, {
      props: {
        submissions: [],
        isLoading: false,
        statusMetaByKey: {},
        isAuthenticated: true,
      },
    });

    expect(wrapper.find("caption").exists()).toBe(false);
    expect(wrapper.text().match(/problem\.submissions\.noSubmissionsDesc/g)).toHaveLength(1);
    expect(wrapper.find("tbody tr").text()).toContain(
      "problem.submissions.noSubmissionsTitle",
    );
  });
});
