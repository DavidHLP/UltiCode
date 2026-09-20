import { describe, expect, it } from "vitest";
import { computed, ref } from "vue";
import { useAvatar } from "@/composables/useAvatar";

describe("useAvatar", () => {
  it("preserves an App-owned custom avatar URL", () => {
    const { normalizedAvatar } = useAvatar(
      ref("admin"),
      ref("/uploads/avatars/admin.png"),
    );

    expect(normalizedAvatar.value).toBe("/uploads/avatars/admin.png");
  });

  it("preserves an object-storage avatar display URL", () => {
    const { normalizedAvatar } = useAvatar(
      ref("admin"),
      ref("/api/users/avatars/admin/avatar.webp"),
    );

    expect(normalizedAvatar.value).toBe("/api/users/avatars/admin/avatar.webp");
  });

  it("generates a deterministic local image when no avatar is stored", () => {
    const first = useAvatar(computed(() => "admin"), computed(() => ""));
    const second = useAvatar(computed(() => "admin"), computed(() => ""));
    const other = useAvatar(computed(() => "alice"), computed(() => ""));

    expect(first.normalizedAvatar.value).toMatch(/^data:image\/svg\+xml/);
    expect(first.normalizedAvatar.value).toBe(second.normalizedAvatar.value);
    expect(first.normalizedAvatar.value).not.toBe(other.normalizedAvatar.value);
  });
});
