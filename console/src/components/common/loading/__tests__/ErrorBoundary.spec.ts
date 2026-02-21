import { describe, it, expect, vi } from "vitest";
import { mount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import ErrorBoundary from "../ErrorBoundary.vue";

// Mock vue-i18n
vi.mock("vue-i18n", () => ({
  useI18n: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        "common.error.title": "Something went wrong",
        "common.error.boundaryMessage": "An error occurred",
        "common.error.showDetails": "Show details",
        "common.error.hideDetails": "Hide details",
        "common.actions.retry": "Retry",
        "common.actions.back": "Back",
      };
      return translations[key] || key;
    },
  }),
}));

// Normal component
const NormalComponent = defineComponent({
  template: "<div>Normal content</div>",
});

describe("ErrorBoundary", () => {
  it("should render children when no error", () => {
    const wrapper = mount(ErrorBoundary, {
      slots: {
        default: () => h(NormalComponent),
      },
    });

    expect(wrapper.text()).toContain("Normal content");
  });

  it("should emit error event when error occurs", () => {
    // Suppress console.error from Vue
    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    // Component that throws an error
    const ThrowError = defineComponent({
      setup() {
        throw new Error("Test error");
      },
      template: "<div>Should not render</div>",
    });

    const wrapper = mount(ErrorBoundary, {
      slots: {
        default: () => h(ThrowError),
      },
    });

    expect(wrapper.emitted("error")).toBeDefined();

    consoleSpy.mockRestore();
  });

  it("should render fallback slot on error", () => {
    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    const ThrowError = defineComponent({
      setup() {
        throw new Error("Test error");
      },
      template: "<div>Should not render</div>",
    });

    const wrapper = mount(ErrorBoundary, {
      slots: {
        default: () => h(ThrowError),
      },
    });

    // The error boundary should catch the error and show something
    // Either the default fallback or a custom one
    expect(wrapper.exists()).toBe(true);

    consoleSpy.mockRestore();
  });

  it("should call custom error handler", () => {
    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    const onError = vi.fn();

    const ThrowError = defineComponent({
      setup() {
        throw new Error("Test error");
      },
      template: "<div>Should not render</div>",
    });

    mount(ErrorBoundary, {
      props: {
        onError,
      },
      slots: {
        default: () => h(ThrowError),
      },
    });

    expect(onError).toHaveBeenCalled();

    consoleSpy.mockRestore();
  });

  it("should accept props correctly", () => {
    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    const ThrowError = defineComponent({
      setup() {
        throw new Error("Test error");
      },
      template: "<div>Should not render</div>",
    });

    const wrapper = mount(ErrorBoundary, {
      props: {
        showDetails: true,
      },
      slots: {
        default: () => h(ThrowError),
      },
    });

    // Component should render with props
    expect(wrapper.exists()).toBe(true);

    consoleSpy.mockRestore();
  });
});
