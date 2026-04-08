import { computed, type MaybeRef, unref } from "vue";

/**
 * Normalize an avatar URL. Returns the custom avatar if it's not a DiceBear URL,
 * otherwise generates a DiceBear avatar using the username as seed.
 */
export function useAvatar(
  username: MaybeRef<string | undefined>,
  avatar: MaybeRef<string | undefined>,
) {
  const normalizedAvatar = computed(() => {
    const name = unref(username) || "anonymous";
    const url = unref(avatar);

    if (url && !url.includes("dicebear.com")) {
      return url;
    }

    return `https://api.dicebear.com/7.x/notionists/svg?seed=${encodeURIComponent(name)}`;
  });

  return { normalizedAvatar };
}
