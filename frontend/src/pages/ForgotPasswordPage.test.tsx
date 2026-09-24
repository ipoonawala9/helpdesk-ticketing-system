import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ForgotPasswordPage } from './ForgotPasswordPage'

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter><ForgotPasswordPage /></MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('ForgotPasswordPage', () => {
  it('asks the backend and then confirms, without saying whether the address has an account', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    renderPage()
    await userEvent.type(screen.getByLabelText('Email'), 'dana@acme.test')
    await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(await screen.findByRole('heading', { name: 'Check your email' })).toBeInTheDocument()
    expect(screen.getByText(/If dana@acme\.test has an account/i)).toBeInTheDocument()
    // Nothing on screen states whether the account exists.
    expect(screen.queryByText(/no account|not found|unknown/i)).toBeNull()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/auth/forgot-password')
    expect(JSON.parse(init.body)).toEqual({ email: 'dana@acme.test' })
    expect(init.headers.Authorization).toBeUndefined()
  })

  it('checks the address before sending anything', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    renderPage()
    await userEvent.type(screen.getByLabelText('Email'), 'not-an-email')
    await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(screen.getByText('Enter the email address you sign in with.')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
