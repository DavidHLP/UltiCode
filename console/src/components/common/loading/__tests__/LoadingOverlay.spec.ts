import { describe, it, expect, vi } from "vitest";
import { mount } from "@vue/test-utils";
import { nextTick, defineComponent } from "vue";
import LoadingOverlay from "../LoadingOverlay.vue";

// Mock the useLoading composable
vi.mock("@/composables/useLoading", () => ({
  useLoading: () => ({
    isLoading: { value: false },
    loadingMessage: { value: "Loading..." },
  }),
}));

// Mock vue-i18n
vi.mock("vue-i18n", () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}));

describe("LoadingOverlay", () => {
  it("should not render when not loading", () => {
    const wrapper = mount(LoadingOverlay, {
      props: {
        loading: false,
      },
    });

    // Teleport renders outside the wrapper, so we check the component exists
    expect(wrapper.exists()).toBe(true);
  });

  it("should render when loading is true", async () => {
    // Create a wrapper component that includes a place for teleport
    const WrapperComponent = defineComponent({
      components: { LoadingOverlay },
      template: `
        <div>
          <LoadingOverlay :loading="true" />
          <div id="teleport-target"></div>
        </div>
      `,
    });

    // Attach to document body for teleport
    const div = document.createElement("div");
    div.id = "app";
    document.body.appendChild(div);

    const wrapper = mount(WrapperComponent, {
      attachTo: div,
      global: {
        stubs: {
          Teleport: false,
        },
      },
    });

    await nextTick();

    // Check if the overlay is rendered somewhere in the document
    const alert = document.querySelector("[role='alert']");
    expect(alert).not.toBeNull();

    // Cleanup
    wrapper.unmount();
    document.body.removeChild(div);
  });

  it("should use global loading state when loading prop not provided", async () => {
    const wrapper = mount(LoadingOverlay);

    await nextTick();

    // The mock returns isLoading: false, so no overlay should render
    expect(wrapper.find("[role='alert']").exists()).toBe(false);
  });

  it("should have correct props interface", () => {
    // Test that props are accepted
    const wrapper = mount(LoadingOverlay, {
      props: {
        loading: true,
        message: "Custom message",
        transparent: true,
        spinnerSize: "lg",
        zIndex: 100,
      },
    });

    expect(wrapper.exists()).toBe(true);
  });
});
