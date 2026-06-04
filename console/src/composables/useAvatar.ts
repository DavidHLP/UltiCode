import { computed, type MaybeRef, unref } from "vue";

/**
 * Normalize an avatar URL. Returns the custom avatar or database avatar URL if it exists,
 * otherwise generates a default avataaars DiceBear avatar using the username as seed.
 */
export function useAvatar(
  username: MaybeRef<string | undefined>,
  avatar: MaybeRef<string | undefined>,
) {
  const normalizedAvatar = computed(() => {
    const name = unref(username) || "anonymous";
    const url = unref(avatar);

    if (url) {
      return url;
    }

    return `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(name)}`;
  });

  return { normalizedAvatar };
}
