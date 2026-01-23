import { apiPost } from "@/utils/request";

export interface User {
  id: string;
  username: string;
  name: string;
  email: string;
  avatar?: string;
  role: string;
  is_active: boolean;
  is_banned: boolean;
  joined_at: string;
}

export interface LoginResponse {
  access_token: string;
  csrf_token?: string;
  user: {
    id: string;
    username: string;
    name: string;
    role: string;
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
