import { afterEach, describe, expect, it, vi } from "vitest";
import { createCsrfTokenManager } from "../csrf";

describe("csrf token cookie fallback", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("restores the exact csrf_token cookie after a hard reload", () => {
    vi.stubGlobal("document", {
      cookie: "other=value; csrf_token=cookie-token; csrf_token_shadow=wrong",
    });

    expect(createCsrfTokenManager().getToken()).toBe("cookie-token");
  });

  it("prefers the latest in-memory token over the cookie fallback", () => {
    vi.stubGlobal("document", { cookie: "csrf_token=cookie-token" });
    const manager = createCsrfTokenManager();

    manager.setToken("response-token");

    expect(manager.getToken()).toBe("response-token");
  });

  it("returns null when no exact csrf cookie exists", () => {
    vi.stubGlobal("document", { cookie: "csrf_token_shadow=wrong" });

    expect(createCsrfTokenManager().getToken()).toBeNull();
  });
});
