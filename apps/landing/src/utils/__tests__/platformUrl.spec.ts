import { afterEach, describe, expect, it, vi } from "vitest";
import { platformUrl } from "../platformUrl";

afterEach(() => vi.unstubAllEnvs());

describe("platformUrl", () => {
  it("uses the Console origin during development", () => {
    expect(platformUrl("/problemset")).toBe("http://localhost:9002/problemset");
  });

  it("rejects non-HTTP platform origins", () => {
    vi.stubEnv("VITE_PLATFORM_URL", "javascript:alert(1)");
    expect(platformUrl("/login")).toBe("http://localhost:9002/login");
  });

  it("accepts configured HTTP platform origins", () => {
    vi.stubEnv("VITE_PLATFORM_URL", "https://oj.example.test");
    expect(platformUrl("/contest")).toBe("https://oj.example.test/contest");
  });
});
