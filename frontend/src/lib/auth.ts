import { UserRecord, AuthResponse } from '../types';

const TOKEN_KEY = 'dete_access_token';
const USER_KEY = 'dete_user_info';

export const DEMO_CREDENTIALS = {
  username: 'demo',
  password: 'DemoPassword123!',
};

export function getAuthToken(): string | null {
  if (typeof window === 'undefined') return null;
  return sessionStorage.getItem(TOKEN_KEY);
}

export function getCurrentUser(): UserRecord | null {
  if (typeof window === 'undefined') return null;
  const raw = sessionStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as UserRecord;
  } catch {
    return null;
  }
}

export function setAuthSession(auth: AuthResponse): void {
  if (typeof window === 'undefined') return;
  sessionStorage.setItem(TOKEN_KEY, auth.accessToken);
  if (auth.user) {
    sessionStorage.setItem(USER_KEY, JSON.stringify(auth.user));
  }
}

export function setCustomUserSession(token: string, user: UserRecord): void {
  if (typeof window === 'undefined') return;
  sessionStorage.setItem(TOKEN_KEY, token);
  sessionStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearAuthSession(): void {
  if (typeof window === 'undefined') return;
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_KEY);
}

export function isAuthenticated(): boolean {
  return Boolean(getAuthToken());
}
