import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { organizationsApi, usersApi } from '../api/endpoints'
import type { Role, User, UserFilters } from '../api/types'
import { ConfirmDialog } from '../components/Dialog'
import { useCurrentUser } from '../auth/AuthContext'
import { EmptyState, ErrorState, SkeletonRows, errorMessage } from '../components/Feedback'
import { SelectField, TextField } from '../components/Fields'
import { Pagination } from '../components/Pagination'
import { CreateUserDialog } from '../components/UserDialogs'
import { ROLE_LABEL, initials } from '../lib/format'
import { useDebounced, useDocumentTitle } from '../lib/hooks'
import { rolePermissions } from '../lib/permissions'
import { queryKeys } from '../lib/queryKeys'
import { notify } from '../lib/toast'

const ROLES: Role[] = ['ORG_ADMIN', 'SUPPORT_AGENT', 'CUSTOMER', 'SUPER_ADMIN']

export function UsersPage() {
  const me = useCurrentUser()
  const isSuper = me.role === 'SUPER_ADMIN'
  useDocumentTitle(isSuper ? 'People' : 'Agents & customers')
  const [params, setParams] = useSearchParams()
  const [adding, setAdding] = useState(false)
  const [deactivating, setDeactivating] = useState<User | null>(null)
  const queryClient = useQueryClient()
  const setActive = useMutation({
    mutationFn: ({ person, active }: { person: User; active: boolean }) =>
      active ? usersApi.activate(person.id) : usersApi.deactivate(person.id),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.users })
      setDeactivating(null)
      notify(updated.active ? `${updated.name} can sign in again.` : `${updated.name} can no longer sign in.`)
    },
    onError: (error) => {
      setDeactivating(null)
      notify(errorMessage(error), 'error')
    },
  })
  // Mirrors the backend: nobody changes their own account, and org admins manage only agents and customers.
  const canToggle = (person: User) => person.id !== me.id
    && (isSuper || person.role === 'SUPPORT_AGENT' || person.role === 'CUSTOMER')
  const [search, setSearch] = useState(params.get('q') ?? '')
  const q = useDebounced(search)

  const roleParam = params.get('role') as Role | null
  const role: Role | undefined = roleParam && ROLES.includes(roleParam) ? roleParam : (isSuper ? undefined : 'SUPPORT_AGENT')
  const organizationId = isSuper && params.get('organizationId') ? Number(params.get('organizationId')) : undefined
  const page = Number(params.get('page') ?? 0) || 0

  const filters: UserFilters = { role, organizationId, q: q || undefined, page, size: 20, sort: 'name,asc' }
  const users = useQuery({
    queryKey: queryKeys.userList(filters),
    queryFn: () => usersApi.list(filters),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  })
  const organizations = useQuery({
    queryKey: queryKeys.organizationList({ size: 100, sort: 'name,asc' }),
    queryFn: () => organizationsApi.list({ size: 100, sort: 'name,asc' }),
    enabled: isSuper,
  })

  function update(changes: Record<string, string | undefined>) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)))
    if (!('page' in changes)) next.delete('page')
    setParams(next, { replace: true })
  }

  const tabs: Role[] = ['SUPPORT_AGENT', 'CUSTOMER']

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>{isSuper ? 'People' : 'Agents & customers'}</h1>
          <p>{isSuper ? 'Every account, across all organizations.' : `Everyone who can sign in to ${me.organization?.name}.`}</p>
        </div>
        <button type="button" className="button" onClick={() => setAdding(true)}>
          {isSuper ? 'Add a person' : role === 'CUSTOMER' ? 'Add a customer' : 'Add an agent'}
        </button>
      </header>

      <section className="card">
        <div className="filters" role="search">
          {!isSuper && (
            <div className="chip-group" role="group" aria-label="Show" style={{ flexBasis: '100%' }}>
              {tabs.map((tab) => (
                <button key={tab} type="button" className="chip" aria-pressed={role === tab} onClick={() => update({ role: tab })}>
                  {tab === 'SUPPORT_AGENT' ? 'Support agents' : 'Customers'}
                </button>
              ))}
            </div>
          )}
          <TextField className="field-grow" label="Search" type="search" placeholder="Name or email" value={search} onChange={(e) => { setSearch(e.target.value); if (page) update({ page: undefined }) }} maxLength={100} />
          {isSuper && (
            <>
              <SelectField label="Role" value={role ?? ''} onChange={(e) => update({ role: e.target.value || undefined })}>
                <option value="">Any role</option>
                {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
              </SelectField>
              <SelectField label="Organization" value={organizationId ? String(organizationId) : ''} onChange={(e) => update({ organizationId: e.target.value || undefined })}>
                <option value="">All organizations</option>
                {organizations.data?.content.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
              </SelectField>
            </>
          )}
        </div>

        {users.isPending && <SkeletonRows rows={5} />}
        {users.isError && <ErrorState error={users.error} onRetry={() => users.refetch()} />}
        {users.data && users.data.content.length === 0 && (
          <EmptyState
            title={q ? 'No one matches that search' : role === 'CUSTOMER' ? 'No customers yet' : role === 'SUPPORT_AGENT' ? 'No support agents yet' : 'No people yet'}
            action={!q && rolePermissions.canManageUsers(me.role) ? <button type="button" className="button button-secondary" onClick={() => setAdding(true)}>Add the first one</button> : undefined}
          >
            {!q && role === 'SUPPORT_AGENT' ? 'Tickets can be assigned once there is at least one agent.' : undefined}
          </EmptyState>
        )}
        {users.data && users.data.content.length > 0 && (
          <div style={{ opacity: users.isPlaceholderData ? 0.6 : 1 }}>
            <div className="table-wrap">
              <table className="table table-responsive">
                <thead>
                  <tr>
                    <th scope="col">Name</th>
                    <th scope="col">Role</th>
                    {isSuper && <th scope="col">Organization</th>}
                    <th scope="col">Phone</th>
                    <th scope="col"><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  {users.data.content.map((person) => (
                    <tr key={person.id}>
                      <td data-wide>
                        <div className="row" style={{ flexWrap: 'nowrap' }}>
                          <span className="avatar" aria-hidden="true">{initials(person.name)}</span>
                          <span style={{ minWidth: 0 }}>
                            <strong>{person.name}</strong>{!person.active && <> <span className="tag tag-inactive">Inactive</span></>}
                            <span className="cell-sub" style={{ overflowWrap: 'anywhere' }}>{person.email}</span>
                          </span>
                        </div>
                      </td>
                      <td>{ROLE_LABEL[person.role]}</td>
                      {isSuper && <td data-hide-mobile>{person.organization?.name ?? <span className="muted">—</span>}</td>}
                      <td data-hide-mobile>{person.phoneNumber || <span className="muted">—</span>}</td>
                      <td data-wide>
                        <div className="row" style={{ justifyContent: 'flex-end' }}>
                          {!isSuper && (person.role === 'SUPPORT_AGENT' || person.role === 'CUSTOMER') && (
                            <Link
                              className="button button-quiet button-small"
                              to={person.role === 'SUPPORT_AGENT' ? `/tickets?assignedAgentId=${person.id}` : `/tickets?customerId=${person.id}`}
                            >
                              {person.role === 'SUPPORT_AGENT' ? 'Assigned tickets' : 'Their tickets'}
                            </Link>
                          )}
                          {canToggle(person) && (person.active ? (
                            <button type="button" className="button button-quiet button-small" style={{ color: 'var(--alert)' }} onClick={() => setDeactivating(person)}>
                              Deactivate
                            </button>
                          ) : (
                            <button type="button" className="button button-secondary button-small" disabled={setActive.isPending} onClick={() => setActive.mutate({ person, active: true })}>
                              Reactivate
                            </button>
                          ))}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={users.data} noun="people" onPage={(p) => update({ page: p ? String(p) : undefined })} />
          </div>
        )}
      </section>

      <ConfirmDialog
        open={deactivating !== null}
        title={`Deactivate ${deactivating?.name ?? ''}?`}
        confirmLabel="Deactivate"
        tone="danger"
        busy={setActive.isPending}
        onCancel={() => setDeactivating(null)}
        onConfirm={() => deactivating && setActive.mutate({ person: deactivating, active: false })}
      >
        {deactivating?.role === 'SUPPORT_AGENT'
          ? 'They are signed out and can no longer sign in. Tickets assigned to them stay assigned, so reassign any that are still open.'
          : 'They are signed out and can no longer sign in. Their tickets and messages are kept, and you can reactivate them later.'}
      </ConfirmDialog>

      <CreateUserDialog
        key={`${role}-${organizationId}`}
        open={adding}
        onClose={() => setAdding(false)}
        defaultRole={isSuper ? (role ?? 'ORG_ADMIN') : (role ?? 'SUPPORT_AGENT')}
        defaultOrganizationId={organizationId}
      />
    </div>
  )
}
