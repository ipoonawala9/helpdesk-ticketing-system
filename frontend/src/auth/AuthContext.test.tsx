import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

const stored = {
  token: 'stored-token',
  expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
  user: { id: 1, name: 'Dana', email: 'd@x.test', role: 'CUSTOMER', phoneNumber: null, active: true, organization: null },
}

describe('AuthProvider', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.resetModules()
  })

  it("sends the stored token on a child component's very first request after a reload", async () => {
    sessionStorage.setItem('helpdesk.session', JSON.stringify(stored))
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(stored.user), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    // Imported after the session is stored, as on a real page load.
    const { AuthProvider } = await import('./AuthContext')
    const { request } = await import('../api/client')

    function Child() {
      // Fires in the child's effect, which React runs before the provider's own effects.
      void request('GET', '/api/tickets')
      return <p>child</p>
    }

    render(
      <QueryClientProvider client={new QueryClient()}>
        <AuthProvider><Child /></AuthProvider>
      </QueryClientProvider>,
    )

    await screen.findByText('child')
    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    const ticketCall = fetchMock.mock.calls.find(([url]) => String(url).includes('/api/tickets'))
    expect(ticketCall?.[1].headers.Authorization).toBe('Bearer stored-token')
  })

  it('ends the session and clears storage when an authenticated request gets 401', async () => {
    sessionStorage.setItem('helpdesk.session', JSON.stringify(stored))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 401, message: 'Invalid or expired access token' }), { status: 401 })))

    const { AuthProvider, useAuth } = await import('./AuthContext')

    function Status() {
      const { user, lastSignOutReason } = useAuth()
      return <p>{user ? 'signed in' : `signed out: ${lastSignOutReason}`}</p>
    }

    render(
      <QueryClientProvider client={new QueryClient()}>
        <AuthProvider><Status /></AuthProvider>
      </QueryClientProvider>,
    )

    // The provider's own profile refresh gets the 401.
    expect(await screen.findByText('signed out: expired')).toBeInTheDocument()
    expect(sessionStorage.getItem('helpdesk.session')).toBeNull()
  })
})
