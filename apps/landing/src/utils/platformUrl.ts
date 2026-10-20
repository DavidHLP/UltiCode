export function platformUrl(path: string): string {
  const platformOrigin = import.meta.env.VITE_PLATFORM_URL;
  const fallbackOrigin = import.meta.env.DEV
    ? `${window.location.protocol}//${window.location.hostname}:9002`
    : window.location.origin;

  try {
    const origin = new URL(
      platformOrigin || fallbackOrigin,
      window.location.origin,
    );
    if (origin.protocol !== "http:" && origin.protocol !== "https:") {
      return new URL(path, fallbackOrigin).href;
    }
    return new URL(path, origin).href;
  } catch {
    return new URL(path, fallbackOrigin).href;
  }
}
