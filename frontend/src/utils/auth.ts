const TOKEN_KEY = "ulticode_token";
const USER_ID_KEY = "ulticode_user_id";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export function setUserId(userId: string) {
  localStorage.setItem(USER_ID_KEY, userId);
}

export function removeUserId() {
  localStorage.removeItem(USER_ID_KEY);
}

export function fetchCurrentUserId(): string | null {
  // Try to get from local storage
  const stored = localStorage.getItem(USER_ID_KEY);
  if (stored) return stored;

  // Return null if no user is authenticated
  return null;
}

export function isAuthenticated(): boolean {
  return !!getToken();
}
