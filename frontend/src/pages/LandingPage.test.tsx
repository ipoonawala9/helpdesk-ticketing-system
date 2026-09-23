import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

async function renderLanding() {
  // Imported per test: AuthProvider reads the stored session when the module
  // first loads, exactly as it does on a real page load.
  const { AuthProvider } = await import('../auth/AuthContext')
  const { LandingPage } = await import('./LandingPage')

  return render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route path="/dashboard" element={<p>dashboard</p>} />
            <Route path="/login" element={<p>sign-in form</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  vi.resetModules()
})

describe('LandingPage', () => {
  it('explains the product to a visitor who is not signed in, and offers sign-in', async () => {
    await renderLanding()

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/Every issue gets a ticket/i)
    expect(screen.getAllByRole('link', { name: 'Sign in' }).length).toBeGreaterThan(0)
    expect(screen.getByRole('heading', { name: 'How a ticket moves' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Four ways in' })).toBeInTheDocument()
    // No sign-up: accounts come from an administrator.
    expect(screen.queryByRole('link', { name: /sign up|register|create account/i })).toBeNull()
  })

  // Set explicitly rather than inherited from a local .env file.
  it('does not offer demo accounts unless the deployment enables them', async () => {
    vi.stubEnv('VITE_DEMO_MODE', 'false')
    vi.stubEnv('VITE_DEMO_PASSWORD', '')

    await renderLanding()

    expect(screen.queryByRole('heading', { name: 'Try it' })).toBeNull()
  })

  it('offers one demo account per role when the deployment enables them', async () => {
    vi.stubEnv('VITE_DEMO_MODE', 'true')
    vi.stubEnv('VITE_DEMO_PASSWORD', 'demo-password')

    await renderLanding()

    expect(screen.getByRole('heading', { name: 'Try it' })).toBeInTheDocument()
    for (const role of ['Customer', 'Support agent', 'Organization admin', 'Super admin']) {
      expect(screen.getByRole('link', { name: new RegExp(`Open as ${role}`, 'i') })).toBeInTheDocument()
    }
  })

  it('sends a signed-in visitor to their dashboard instead of the pitch', async () => {
    sessionStorage.setItem('helpdesk.session', JSON.stringify({
      token: 't',
      expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
      user: { id: 1, name: 'Dana', email: 'd@x.test', role: 'CUSTOMER', phoneNumber: null, active: true, organization: null },
    }))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 200 })))

    await renderLanding()

    expect(screen.getByText('dashboard')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'How a ticket moves' })).toBeNull()
  })
})
