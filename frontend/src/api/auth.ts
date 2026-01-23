import { apiPost } from "@/utils/request";

export interface LoginResponse {
  access_token: string;
  user: {
    id: string;
    username: string;
    name: string;
  };
}

export async function login(
  data: Record<string, unknown>,
): Promise<LoginResponse> {
  return apiPost<LoginResponse>("/auth/login", data);
}

export async function register(
  data: Record<string, unknown>,
): Promise<LoginResponse> {
  return apiPost<LoginResponse>("/auth/register", data);
}

export async function forgotPassword(
  email: string,
): Promise<{ message: string }> {
  return apiPost("/auth/forgot-password", { email });
}

export async function resetPassword(
  token: string,
  newPassword: string,
): Promise<{ message: string }> {
  return apiPost("/auth/reset-password", { token, newPassword });
}

export async function logout() {
  return apiPost<void>("/auth/logout");
}
