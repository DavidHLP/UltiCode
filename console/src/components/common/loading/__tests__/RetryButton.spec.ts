import { describe, it, expect, vi } from "vitest";
import { mount } from "@vue/test-utils";
import RetryButton from "../RetryButton.vue";

// Mock vue-i18n
vi.mock("vue-i18n", () => ({
  useI18n: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        "common.status.processing": "Processing...",
        "common.actions.retry": "Retry",
      };
      return translations[key] || key;
    },
  }),
}));

describe("RetryButton", () => {
  it("should render with default text", () => {
    const wrapper = mount(RetryButton);

    expect(wrapper.text()).toContain("Retry");
  });

  it("should be disabled when retrying", () => {
    const wrapper = mount(RetryButton, {
      props: {
        retrying: true,
      },
    });

    const button = wrapper.find("button");
    expect(button.attributes("disabled")).toBeDefined();
    expect(wrapper.text()).toContain("Processing");
  });

  it("should be disabled when countdown is active", () => {
    const wrapper = mount(RetryButton, {
      props: {
        countdown: 5,
      },
    });

    const button = wrapper.find("button");
    expect(button.attributes("disabled")).toBeDefined();
    // When countdown is active, the button text should show countdown
    // The component computes buttonText which includes the countdown
    expect(wrapper.text()).toContain("Retry"); // Base text is always present
  });

  it("should show attempt count when provided", () => {
    const wrapper = mount(RetryButton, {
      props: {
        attempt: 2,
        maxAttempts: 3,
      },
    });

    expect(wrapper.text()).toContain("2/3");
  });

  it("should emit retry event when clicked", async () => {
    const wrapper = mount(RetryButton);

    await wrapper.find("button").trigger("click");

    expect(wrapper.emitted("retry")).toHaveLength(1);
  });

  it("should not emit retry event when disabled", async () => {
    const wrapper = mount(RetryButton, {
      props: {
        disabled: true,
      },
    });

    await wrapper.find("button").trigger("click");

    expect(wrapper.emitted("retry")).toBeUndefined();
  });

  it("should apply variant prop", () => {
    const wrapper = mount(RetryButton, {
      props: {
        variant: "destructive",
      },
    });

    const button = wrapper.find("button");
    expect(button.classes().some((c) => c.includes("destructive"))).toBe(true);
  });

  it("should accept size prop", () => {
    const wrapper = mount(RetryButton, {
      props: {
        size: "lg",
      },
    });

    // Just check the component renders with the prop
    expect(wrapper.exists()).toBe(true);
  });

  it("should show spinning icon when retrying", () => {
    const wrapper = mount(RetryButton, {
      props: {
        retrying: true,
      },
    });

    const icon = wrapper.find(".animate-spin");
    expect(icon.exists()).toBe(true);
  });

  it("should hide countdown when showCountdown is false", () => {
    const wrapper = mount(RetryButton, {
      props: {
        countdown: 5,
        showCountdown: false,
      },
    });

    // Should not show countdown text, just "Retry"
    expect(wrapper.text()).not.toContain("5s");
    expect(wrapper.text()).toContain("Retry");
  });

  it("should render slot content", () => {
    const wrapper = mount(RetryButton, {
      slots: {
        default: "Custom Retry Text",
      },
    });

    expect(wrapper.text()).toContain("Custom Retry Text");
  });
});
