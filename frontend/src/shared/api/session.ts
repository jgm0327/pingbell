// Non-React external store for the current auth session. Lives outside React so the plain HTTP
// client (httpClient.ts) can read/refresh/clear it without needing a hook, while AuthContext
// mirrors it into React state via useSyncExternalStore.

export interface Session {
  email: string
  accessToken: string
  refreshToken: string
}

const STORAGE_KEY = 'pingbell.session'

type Listener = () => void
const listeners = new Set<Listener>()
const expiryListeners = new Set<Listener>()

function readFromStorage(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

let current: Session | null = readFromStorage()

export function getSession(): Session | null {
  return current
}

export function setSession(next: Session | null): void {
  current = next
  try {
    if (next) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    // Ignore storage failures (private browsing, quota, etc.) - the in-memory value still holds.
  }
  listeners.forEach((listener) => listener())
}

export function subscribeSession(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** Fired only when a session is cleared because refreshing it failed - not on explicit logout. */
export function notifySessionExpired(): void {
  expiryListeners.forEach((listener) => listener())
}

export function subscribeSessionExpired(listener: Listener): () => void {
  expiryListeners.add(listener)
  return () => expiryListeners.delete(listener)
}
