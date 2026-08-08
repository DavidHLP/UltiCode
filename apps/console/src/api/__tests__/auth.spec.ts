import { describe, it, expect, vi, beforeEach } from "vitest";
import { apiGet, apiPost } from "@/utils/request";
import { authApi } from "@/api/auth";
import type { User } from "@/types/auth";

vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}));

const mockUser: User = {
  id: "1",
  username: "testuser",
  name: "Test User",
  email: "test@example.com",
  role: "USER",
  isActive: true,
  joinedAt: "2026-01-01T00:00:00Z",
};

describe("authApi", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("login", () => {
    it("calls apiPost with /auth/login and credentials", async () => {
      const credentials = { username: "testuser", password: "password123" };
      const loginResponse = { csrfToken: "csrf-123", user: mockUser };
      vi.mocked(apiPost).mockResolvedValue(loginResponse);

      const result = await authApi.login(credentials);

      expect(apiPost).toHaveBeenCalledWith("/auth/login", credentials);
      expect(result).toEqual(loginResponse);
    });
  });

  describe("register", () => {
    it("calls apiPost with /auth/register and registration data", async () => {
      const registerData = {
        username: "newuser",
        password: "password123",
        email: "new@example.com",
        name: "New User",
      };
      const registerResponse = { csrfToken: "csrf-456", user: mockUser };
      vi.mocked(apiPost).mockResolvedValue(registerResponse);

      const result = await authApi.register(registerData);

      expect(apiPost).toHaveBeenCalledWith("/auth/register", registerData);
      expect(result).toEqual(registerResponse);
    });
  });

  describe("logout", () => {
    it("calls apiPost with /auth/logout", async () => {
      vi.mocked(apiPost).mockResolvedValue(undefined);

      await authApi.logout();

      expect(apiPost).toHaveBeenCalledWith("/auth/logout");
    });
  });

  describe("getCurrentUser", () => {
    it("calls apiGet with /auth/me and returns the user", async () => {
      const meResponse = { user: mockUser, csrfToken: "csrf-789" };
      vi.mocked(apiGet).mockResolvedValue(meResponse);

      const result = await authApi.getCurrentUser();

      expect(apiGet).toHaveBeenCalledWith("/auth/me");
      expect(result).toEqual(mockUser);
    });
  });

  describe("forgotPassword", () => {
    it("calls apiPost with /auth/forgot-password and email", async () => {
      const request = { email: "test@example.com" };
      vi.mocked(apiPost).mockResolvedValue(undefined);

      await authApi.forgotPassword(request);

      expect(apiPost).toHaveBeenCalledWith("/auth/forgot-password", request);
    });
  });

  describe("resetPassword", () => {
    it("calls apiPost with /auth/reset-password and token data", async () => {
      const request = { token: "reset-token-123", newPassword: "newPass456" };
      vi.mocked(apiPost).mockResolvedValue(undefined);

      await authApi.resetPassword(request);

      expect(apiPost).toHaveBeenCalledWith("/auth/reset-password", request);
    });
  });
});
