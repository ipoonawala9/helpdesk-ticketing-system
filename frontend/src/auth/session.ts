import type { LoginResponse, User } from '../api/types'

/**
 * The signed-in session: access token, its expiry, and the user.
 *
 * Kept in sessionStorage, not localStorage, so it survives a page reload but
 * is discarded when the tab closes and is never shared across tabs. The
 * backend issues bearer tokens rather than httpOnly cookies, so the token has
 * to be readable by this code; the defence against it being stolen is that the
 * app renders all user content as text, never as HTML, and loads no
 * third-party scripts.
 */
export interface Session {
  token: string
  expiresAt: string
  user: User
}

const KEY = 'helpdesk.session'

export function sessionFromLogin(response: LoginResponse): Session {
  return { token: response.accessToken, expiresAt: response.expiresAt, user: response.user }
}

export function isExpired(session: Session, now: number = Date.now()): boolean {
  return new Date(session.expiresAt).getTime() <= now
}

export function loadSession(now: number = Date.now()): Session | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    if (!session.token || !session.expiresAt || !session.user || isExpired(session, now)) {
      sessionStorage.removeItem(KEY)
      return null
    }
    return session
  } catch {
    return null
  }
}

export function saveSession(session: Session) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(session))
  } catch {
    // Storage can be unavailable (private mode); the session then lasts for this page only.
  }
}

export function clearSession() {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    // Nothing to clear.
  }
}
