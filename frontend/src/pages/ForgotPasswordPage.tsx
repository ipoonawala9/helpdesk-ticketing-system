import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { authApi } from '../api/endpoints'
import { BrandMark } from '../components/Brand'
import { Notice, errorMessage } from '../components/Feedback'
import { TextField } from '../components/Fields'
import { useDocumentTitle } from '../lib/hooks'

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function ForgotPasswordPage() {
  useDocumentTitle('Reset your password')
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)

  const request = useMutation({ mutationFn: () => authApi.forgotPassword(email.trim()) })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!EMAIL.test(email.trim())) {
      setError('Enter the email address you sign in with.')
      return
    }
    setError(null)
    request.mutate()
  }

  return (
    <div className="narrow-page">
      <Link className="brand" to="/"><BrandMark size={24} /> HelpDesk</Link>

      {request.isSuccess ? (
        <section className="card card-body stack" aria-live="polite">
          <h1 style={{ fontSize: 'var(--text-lg)' }}>Check your email</h1>
          <p className="muted">
            If {email.trim()} has an account, a link to set a new password is on its way. The link works once and
            expires in 30 minutes.
          </p>
          <p className="field-hint">
            Nothing arrived? Check spam, or ask your organization's administrator, who can also set your password.
          </p>
          <div className="form-actions">
            <Link className="button button-secondary" to="/login">Back to sign in</Link>
          </div>
        </section>
      ) : (
        <form className="card card-body stack" onSubmit={submit} noValidate>
          <div className="stack" style={{ gap: 'var(--space-2)' }}>
            <h1 style={{ fontSize: 'var(--text-lg)' }}>Reset your password</h1>
            <p className="muted">Tell us the address you sign in with and we'll send a link to set a new password.</p>
          </div>
          {request.isError && <Notice tone="error">{errorMessage(request.error)}</Notice>}
          <TextField
            label="Email"
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={error ?? undefined}
            autoFocus
            required
          />
          <div className="form-actions">
            <Link className="button button-secondary" to="/login">Cancel</Link>
            <button type="submit" className="button" disabled={request.isPending}>
              {request.isPending ? 'Sending…' : 'Send reset link'}
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
