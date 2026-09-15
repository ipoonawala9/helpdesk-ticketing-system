import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import type { Ticket } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { BrandMark } from '../components/Brand'
import { Notice, errorMessage } from '../components/Feedback'
import { TextField } from '../components/Fields'
import { TicketStub } from '../components/TicketBits'
import { useDocumentTitle } from '../lib/hooks'

// An illustration of the ticket stub, not data from the API.
const SAMPLE: Ticket = {
  id: 0, ticketNumber: 'HD-2026-000128', title: 'VPN drops every few minutes', description: '',
  status: 'IN_PROGRESS', priority: 'HIGH', category: 'NETWORK',
  customer: { id: 0, name: 'Priya Shah', email: '', role: 'CUSTOMER' },
  assignedAgent: { id: 0, name: 'Sam Ortiz', email: '', role: 'SUPPORT_AGENT' },
  organization: { id: 0, name: 'Northwind' }, reopenCount: 0,
  createdAt: new Date(Date.now() - 3 * 3600_000).toISOString().slice(0, 19),
  updatedAt: new Date(Date.now() - 12 * 60_000).toISOString().slice(0, 19),
  resolvedAt: null, closedAt: null,
}

export function LoginPage() {
  useDocumentTitle('Sign in')
  const { user, signIn, lastSignOutReason } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/dashboard'
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const login = useMutation({
    mutationFn: () => signIn(email.trim(), password),
    onSuccess: () => navigate(from, { replace: true }),
  })

  if (user) return <Navigate to={from} replace />

  function submit(event: FormEvent) {
    event.preventDefault()
    if (email.trim() && password) login.mutate()
  }

  return (
    <div className="login">
      <section className="login-art" aria-hidden="true">
        <div className="brand" style={{ padding: 0 }}><BrandMark size={26} /> HelpDesk</div>
        <div className="stack" style={{ gap: 'var(--space-5)' }}>
          <h1 className="login-headline">Every issue gets a ticket. Every ticket gets seen through.</h1>
          <p>Customers report problems, admins route them to the right agent, and everyone sees exactly where a ticket stands until it's closed.</p>
        </div>
        <div className="login-demo-stub"><TicketStub ticket={SAMPLE} /></div>
      </section>

      <section className="login-panel">
        <div className="stack" style={{ gap: 'var(--space-2)' }}>
          <h2 style={{ fontSize: 'var(--text-lg)' }}>Sign in</h2>
          <p className="muted">Use the email and password your organization gave you.</p>
        </div>

        {lastSignOutReason === 'expired' && !login.isError && (
          <Notice>Your session expired. Sign in again to continue.</Notice>
        )}
        {login.isError && <Notice tone="error">{errorMessage(login.error)}</Notice>}

        <form className="stack" onSubmit={submit}>
          <TextField label="Email" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
          <TextField label="Password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          <button type="submit" className="button button-block" disabled={login.isPending || !email.trim() || !password}>
            {login.isPending ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <p className="field-hint">No account? Ask your organization's HelpDesk administrator to add you.</p>
      </section>
    </div>
  )
}
