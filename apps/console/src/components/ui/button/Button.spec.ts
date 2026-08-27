import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import Button from "./Button.vue";

describe("Button shadcn contract", () => {
  it("exposes resolved default state", () => {
    const button = mount(Button).get("button");

    expect(button.attributes("data-variant")).toBe("default");
    expect(button.attributes("data-size")).toBe("default");
  });

  it("exposes variant and compact size state", () => {
    const button = mount(Button, {
      props: { variant: "outline", size: "icon-xs" },
    }).get("button");

    expect(button.attributes("data-variant")).toBe("outline");
    expect(button.attributes("data-size")).toBe("icon-xs");
    expect(button.classes()).toEqual(
      expect.arrayContaining(["size-6", "focus-visible:ring-3"]),
    );
  });
});
