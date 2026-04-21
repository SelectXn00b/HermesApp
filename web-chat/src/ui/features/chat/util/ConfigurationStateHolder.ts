const TOKEN_KEY = 'operit-web-chat-token';

export function readStoredToken() {
  if (typeof window === 'undefined') {
    return '';
  }
  return window.localStorage.getItem(TOKEN_KEY) ?? '';
}

export function writeStoredToken(token: string) {
  if (typeof window === 'undefined') {
    return;
  }

  if (!token.trim()) {
    window.localStorage.removeItem(TOKEN_KEY);
    return;
  }

  window.localStorage.setItem(TOKEN_KEY, token.trim());
}

export function clearStoredToken() {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.removeItem(TOKEN_KEY);
}
