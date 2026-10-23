import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import CreateListDialog from "../CreateListDialog.vue";

vi.mock("vue-i18n", async (original) => ({
  ...(await original<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => key }),
}));

describe("CreateListDialog", () => {
  it("submits the selected visibility and resets it when cancelled", async () => {
    const slot = { template: "<div><slot /></div>" };
    const wrapper = mount(CreateListDialog, {
      props: { open: true, loading: false },
      global: {
        stubs: {
          Dialog: slot,
          DialogContent: slot,
          DialogTitle: slot,
          DialogDescription: slot,
        },
      },
    });
    try {
      await wrapper.get("#create-name").setValue("Practice");
      await wrapper.get('[role="switch"]').trigger("click");
      await wrapper
        .findAll("button")
        .find(
          (button) =>
            button.text() === "personal.problemLists.dialogs.createButton",
        )!
        .trigger("click");
      expect(wrapper.emitted("submit")?.[0]).toEqual([
        { name: "Practice", description: "", isPublic: true },
      ]);
      await wrapper
        .findAll("button")
        .find((button) => button.text() === "common.actions.cancel")!
        .trigger("click");
      expect(wrapper.get('[role="switch"]').attributes("aria-checked")).toBe(
        "false",
      );
      expect(
        (wrapper.get("#create-name").element as HTMLInputElement).value,
      ).toBe("");
    } finally {
      wrapper.unmount();
    }
  });
});
