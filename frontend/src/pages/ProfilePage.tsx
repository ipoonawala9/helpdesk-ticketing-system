import { useMutation, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { authApi } from '../api/endpoints'
import { useAuth, useCurrentUser } from '../auth/AuthContext'
import { ErrorState, Notice, SkeletonRows, errorMessage } from '../components/Feedback'
import { PasswordField } from '../components/Fields'
import { ROLE_LABEL, initials } from '../lib/format'
import { useDocumentTitle } from '../lib/hooks'
import { queryKeys } from '../lib/queryKeys'
import { notify } from '../lib/toast'

const ROLE_DESCRIPTION = {
  CUSTOMER: 'You can report problems, follow your tickets, message the agent working on them, and close or reopen them.',
  SUPPORT_AGENT: 'You work on tickets assigned to you: start work, resolve, and talk with the customer.',
  ORG_ADMIN: 'You manage your organization: assign tickets to agents and add agents and customers.',
  SUPER_ADMIN: 'You manage organizations and people across the whole system, and can view every ticket.',
} as const

const sessionEnds = new Intl.DateTimeFormat(undefined, { timeStyle: 'short' })

export function ProfilePage() {
  useDocumentTitle('Profile')
  const stored = useCurrentUser()
  const { session, signOut } = useAuth()
  const me = useQuery({ queryKey: queryKeys.me, queryFn: authApi.me, initialData: stored })

  return (
    <div className="page" style={{ maxWidth: 760 }}>
      <header className="page-header">
        <div className="row" style={{ gap: 'var(--space-4)' }}>
          <span className="avatar" style={{ width: 56, height: 56, fontSize: 'var(--text-md)', background: 'var(--stub)' }} aria-hidden="true">{initials(stored.name)}</span>
          <div>
            <h1>{me.data.name}</h1>
            <p>{ROLE_LABEL[me.data.role]}{me.data.organization ? ` at ${me.data.organization.name}` : ''}</p>
          </div>
        </div>
      </header>

      <section className="card">
        <div className="card-header"><h2>Account</h2></div>
        {me.isFetching && !me.isFetched ? <SkeletonRows rows={3} /> : me.isError ? <ErrorState error={me.error} /> : (
          <div className="card-body">
            <dl className="definition">
              <dt>Email</dt><dd>{me.data.email}</dd>
              <dt>Phone</dt><dd>{me.data.phoneNumber || <span className="muted">Not set</span>}</dd>
              <dt>Role</dt><dd>{ROLE_LABEL[me.data.role]}</dd>
              <dt>Organization</dt><dd>{me.data.organization?.name ?? <span className="muted">All organizations</span>}</dd>
            </dl>
          </div>
        )}
        <hr className="divider" />
        <div className="card-body"><p className="muted">{ROLE_DESCRIPTION[me.data.role]}</p></div>
      </section>

      <ChangePassword />

      <section className="card">
        <div className="card-header"><h2>Session</h2></div>
        <div className="card-body row" style={{ justifyContent: 'space-between' }}>
          <p className="muted">
            {session ? <>You'll be signed out at <span className="mono">{sessionEnds.format(new Date(session.expiresAt))}</span>.</> : null}
          </p>
          <button type="button" className="button button-secondary" onClick={() => signOut()}>Sign out</button>
        </div>
      </section>
    </div>
  )
}

function ChangePassword() {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const change = useMutation({
    mutationFn: () => authApi.changePassword(current, next),
    onSuccess: () => {
      setCurrent('')
      setNext('')
      setConfirm('')
      setErrors({})
      notify('Password changed. Use the new one next time you sign in.')
    },
    onError: (error) => {
      if (error instanceof ApiError) setErrors(error.fieldErrors)
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    const found: Record<string, string> = {}
    if (!current) found.currentPassword = 'Enter your current password.'
    if (next.length < 8) found.newPassword = 'Use at least 8 characters.'
    else if (next === current) found.newPassword = 'Choose a password different from the current one.'
    if (confirm !== next) found.confirmPassword = "The passwords don't match."
    setErrors(found)
    if (Object.keys(found).length === 0) change.mutate()
  }

  const fieldErrorShown = change.error instanceof ApiError && Object.keys(change.error.fieldErrors).length > 0

  return (
    <section className="card" aria-labelledby="password-title">
      <div className="card-header">
        <div>
          <h2 id="password-title">Password</h2>
          <p>If an administrator set your password, replace it with one only you know.</p>
        </div>
      </div>
      <form className="card-body stack" onSubmit={submit} noValidate>
        {change.isError && !fieldErrorShown && <Notice tone="error">{errorMessage(change.error)}</Notice>}
        <PasswordField label="Current password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} error={errors.currentPassword} />
        <div className="form-grid">
          <PasswordField label="New password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} error={errors.newPassword} hint="At least 8 characters." maxLength={100} />
          <PasswordField label="Confirm new password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} error={errors.confirmPassword} maxLength={100} />
        </div>
        <div className="form-actions">
          <button type="submit" className="button" disabled={change.isPending}>{change.isPending ? 'Changing…' : 'Change password'}</button>
        </div>
      </form>
    </section>
  )
}
