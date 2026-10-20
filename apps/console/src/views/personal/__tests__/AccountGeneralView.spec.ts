import { shallowMount, flushPromises } from "@vue/test-utils";
import { ref } from "vue";
import { createI18n } from "vue-i18n";
import { describe, expect, it, vi } from "vitest";
import AccountGeneralView from "../AccountGeneralView.vue";

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ fetchCurrentUserId: () => "user-1" }),
}));
vi.mock("@/api/user", () => ({
  fetchUserProfile: vi
    .fn()
    .mockResolvedValue({ id: "user-1", username: "test" }),
  updateMyProfile: vi.fn(),
}));
vi.mock("@/composables/useAvatar", () => ({
  useAvatar: () => ({ normalizedAvatar: ref("/avatar.png") }),
}));
vi.mock("@/composables/useAvatarUpload", () => ({
  useAvatarUpload: () => ({
    uploading: ref(false),
    progress: ref(0),
    upload: vi.fn(),
  }),
  isAvatarUploadSessionCurrent: vi.fn(),
}));

describe("account avatar layout", () => {
  it("gives the upload hint a wrapping basis before the action consumes the row", async () => {
    const wrapper = shallowMount(AccountGeneralView, {
      global: {
        plugins: [
          createI18n({
            legacy: false,
            locale: "en-US",
            missingWarn: false,
            fallbackWarn: false,
          }),
        ],
        renderStubDefaultSlot: true,
      },
    });
    try {
      await flushPromises();
      const hint = wrapper.get("img").element.nextElementSibling!;
      expect(hint.classList).toContain("basis-32");
      expect(wrapper.get("img").element.parentElement?.classList).toContain(
        "flex-wrap",
      );
    } finally {
      wrapper.unmount();
    }
  });
});
