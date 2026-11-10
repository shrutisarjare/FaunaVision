// Single place that knows where the Spring Boot API lives.
// VITE_API_URL wins when it is set (split deployments). Otherwise the dev server talks to
// the backend on :8080, while a production build is served *by* that backend, so the API
// is simply same-origin — no CORS and no localhost baked into the bundle.
const configured = import.meta.env.VITE_API_URL?.trim();

export const API_URL = (
  configured || (import.meta.env.DEV ? "http://localhost:8080" : "")
).replace(/\/+$/, "");

export const apiUrl = (path) => `${API_URL}${path}`;

// The session lives in two keys. Login, register and the logout buttons all go through
// the helpers below so they never disagree about what is stored.
const TOKEN_KEY = "token";
const USER_KEY = "user";

/** Stores what `/api/auth/login` and `/api/auth/register` return. */
export const saveSession = ({ token, user } = {}) => {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
};

/** Drops the session but keeps unrelated preferences (theme, autosave, ...). */
export const clearSession = () => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
};

/**
 * The signed-in user, or null when storage is empty or corrupt. A leftover user without
 * a token (written by the old Express build) is not a session: the API would reject it.
 */
export const currentUser = () => {
  try {
    const user = JSON.parse(localStorage.getItem(USER_KEY));
    return localStorage.getItem(TOKEN_KEY) && user ? user : null;
  } catch {
    return null;
  }
};

export const authToken = () => localStorage.getItem(TOKEN_KEY);

/** Headers for a request that the API identifies by the stored token. */
export const authHeaders = (extra = {}) => {
  const token = authToken();
  return token ? { ...extra, Authorization: `Bearer ${token}` } : { ...extra };
};

/**
 * Re-reads the account behind the stored token, so the UI shows the server's copy rather
 * than a value cached at login. A rejected token is dropped; an unreachable API is not.
 */
export const refreshSession = async () => {
  if (!authToken()) return null;
  try {
    const response = await fetch(apiUrl("/api/auth/me"), { headers: authHeaders() });
    if (response.status === 401) {
      clearSession();
      return null;
    }
    if (!response.ok) return currentUser();
    const user = await response.json();
    saveSession({ user });
    return user;
  } catch {
    // Backend down: keep the stored session instead of signing the user out.
    return currentUser();
  }
};

/** The `message` the API sends with a 4xx/5xx response, ready to display. */
export const errorMessage = async (response, fallback) => {
  try {
    const data = await response.json();
    return data.message || data.error || fallback;
  } catch {
    return fallback;
  }
};
