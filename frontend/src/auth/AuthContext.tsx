import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useState, type ReactNode } from 'react'
import { configureApiClient } from '../api/client'
import { authApi } from '../api/endpoints'
import type { User } from '../api/types'
import { clearSession, loadSession, saveSession, sessionFromLogin, type Session } from './session'

export type SignOutReason = 'signed-out' | 'expired'

// The API client reads the session from here, outside React, so the very first
// request after a page load already carries the token. Configuring it from a
// component effect would run after child components had started fetching.
let activeSession: Session | null = loadSession()
let handleUnauthorized: () => void = () => {
  activeSession = null
  clearSession()
  window.location.assign('/login')
}

configureApiClient({
  getToken: () => activeSession?.token ?? null,
  onUnauthorized: () => handleUnauthorized(),
})

interface AuthState {
  user: User | null
  session: Session | null
  signIn: (email: string, password: string) => Promise<User>
  signOut: (reason?: SignOutReason) => void
  /** Why the last session ended, shown once on the login page. */
  lastSignOutReason: SignOutReason | null
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(activeSession)
  const [lastSignOutReason, setLastSignOutReason] = useState<SignOutReason | null>(null)

  const applySession = useCallback((next: Session | null) => {
    activeSession = next
    if (next) saveSession(next)
    else clearSession()
    setSession(next)
  }, [])

  const signOut = useCallback(
    (reason: SignOutReason = 'signed-out') => {
      applySession(null)
      setLastSignOutReason(reason)
      // Drop every cached response so the next user never sees the previous one's data.
      queryClient.clear()
    },
    [applySession, queryClient],
  )

  useLayoutEffect(() => {
    handleUnauthorized = () => {
      if (activeSession) signOut('expired')
    }
  }, [signOut])

  // End the session when the token expires, rather than on the next failed request.
  useEffect(() => {
    if (!session) return
    const remaining = new Date(session.expiresAt).getTime() - Date.now()
    const timer = window.setTimeout(() => signOut('expired'), Math.max(remaining, 0))
    return () => window.clearTimeout(timer)
  }, [session, signOut])

  // Refresh the stored profile on load, so a role or organization change is picked up.
  useEffect(() => {
    if (!activeSession) return
    authApi
      .me()
      .then((user) => {
        if (activeSession) applySession({ ...activeSession, user })
      })
      .catch(() => {
        // A 401 has already signed the user out; other failures keep the stored profile.
      })
  }, [applySession])

  const signIn = useCallback(
    async (email: string, password: string) => {
      const response = await authApi.login(email, password)
      queryClient.clear()
      applySession(sessionFromLogin(response))
      setLastSignOutReason(null)
      return response.user
    },
    [applySession, queryClient],
  )

  const value = useMemo<AuthState>(
    () => ({ user: session?.user ?? null, session, signIn, signOut, lastSignOutReason }),
    [session, signIn, signOut, lastSignOutReason],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}

/** The signed-in user. Only for components rendered behind RequireAuth. */
export function useCurrentUser(): User {
  const { user } = useAuth()
  if (!user) throw new Error('useCurrentUser requires a signed-in user')
  return user
}
