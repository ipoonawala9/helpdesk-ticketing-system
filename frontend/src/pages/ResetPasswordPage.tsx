import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { authApi } from '../api/endpoints'
import { BrandMark } from '../components/Brand'
import { Notice, errorMessage } from '../components/Feedback'
import { PasswordField } from '../components/Fields'
import { useDocumentTitle } from '../lib/hooks'
import { notify } from '../lib/toast'

export function ResetPasswordPage() {
  useDocumentTitle('Choose a new password')
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const reset = useMutation({
    mutationFn: () => authApi.resetPassword(token, password),
    onSuccess: () => {
      notify('Password changed. Sign in with your new password.')
      navigate('/login', { replace: true })
    },
    onError: (error) => {
      if (error instanceof ApiError) setErrors(error.fieldErrors)
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    const found: Record<string, string> = {}
    if (password.length < 8) found.newPassword = 'Use at least 8 characters.'
    if (confirm !== password) found.confirmPassword = "The passwords don't match."
    setErrors(found)
    if (Object.keys(found).length === 0) reset.mutate()
  }

  if (!token) {
    return (
      <div className="narrow-page">
        <Link className="brand" to="/"><BrandMark size={24} /> HelpDesk</Link>
        <section className="card card-body stack">
          <h1 style={{ fontSize: 'var(--text-lg)' }}>This link is incomplete</h1>
          <p className="muted">Open the link from your email, or ask for a new one.</p>
          <div className="form-actions">
            <Link className="button" to="/forgot-password">Ask for a new link</Link>
          </div>
        </section>
      </div>
    )
  }

  const linkRejected = errors.token

  return (
    <div className="narrow-page">
      <Link className="brand" to="/"><BrandMark size={24} /> HelpDesk</Link>
      <form className="card card-body stack" onSubmit={submit} noValidate>
        <div className="stack" style={{ gap: 'var(--space-2)' }}>
          <h1 style={{ fontSize: 'var(--text-lg)' }}>Choose a new password</h1>
          <p className="muted">Pick something you don't use anywhere else.</p>
        </div>

        {linkRejected && (
          <Notice tone="error">
            {linkRejected} <Link to="/forgot-password">Ask for a new link</Link>.
          </Notice>
        )}
        {reset.isError && !(reset.error instanceof ApiError && Object.keys(reset.error.fieldErrors).length) && (
          <Notice tone="error">{errorMessage(reset.error)}</Notice>
        )}

        <PasswordField
          label="New password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.newPassword}
          hint="At least 8 characters."
          maxLength={100}
          autoFocus
        />
        <PasswordField
          label="Confirm new password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          error={errors.confirmPassword}
          maxLength={100}
        />
        <div className="form-actions">
          <Link className="button button-secondary" to="/login">Cancel</Link>
          <button type="submit" className="button" disabled={reset.isPending}>
            {reset.isPending ? 'Saving…' : 'Set new password'}
          </button>
        </div>
      </form>
    </div>
  )
}
