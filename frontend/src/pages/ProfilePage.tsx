import { useQuery } from '@tanstack/react-query'
import { authApi } from '../api/endpoints'
import { useAuth, useCurrentUser } from '../auth/AuthContext'
import { ErrorState, SkeletonRows } from '../components/Feedback'
import { ROLE_LABEL, initials } from '../lib/format'
import { useDocumentTitle } from '../lib/hooks'
import { queryKeys } from '../lib/queryKeys'

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
