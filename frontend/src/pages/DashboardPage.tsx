import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { organizationsApi, ticketsApi, usersApi } from '../api/endpoints'
import type { Ticket, TicketFilters, TicketStatus, User } from '../api/types'
import { useCurrentUser } from '../auth/AuthContext'
import { EmptyState, ErrorState, SkeletonRows } from '../components/Feedback'
import { StatusLedger } from '../components/StatusLedger'
import { PriorityBadge, StatusIndicator } from '../components/TicketBits'
import { formatRelative } from '../lib/format'
import { useDocumentTitle } from '../lib/hooks'
import { queryKeys } from '../lib/queryKeys'

function ticketsLink(filters: Record<string, string | string[]>) {
  const params = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) =>
    (Array.isArray(value) ? value : [value]).forEach((item) => params.append(key, item)))
  return `/tickets?${params}`
}

/** A short, focused list of tickets that need the viewer's attention. */
function Queue({ title, description, filters, empty, viewAll }: {
  title: string
  description: string
  filters: TicketFilters
  empty: string
  viewAll: string
}) {
  const query = useQuery({
    queryKey: queryKeys.ticketList({ ...filters, size: 6 }),
    queryFn: ({ signal }) => ticketsApi.list({ ...filters, size: 6 }, signal),
  })

  return (
    <section className="card" aria-labelledby={`queue-${title}`}>
      <div className="card-header">
        <div>
          <h2 id={`queue-${title}`}>{title}</h2>
          <p>{description}</p>
        </div>
        {query.data && query.data.totalElements > 0 && (
          <Link className="button button-quiet button-small" to={viewAll}>
            View all <span className="mono">{query.data.totalElements}</span>
          </Link>
        )}
      </div>
      {query.isPending && <SkeletonRows rows={3} />}
      {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}
      {query.data && query.data.content.length === 0 && <EmptyState title={empty} />}
      {query.data && query.data.content.length > 0 && (
        <ul className="queue">
          {query.data.content.map((ticket) => <QueueItem key={ticket.id} ticket={ticket} />)}
        </ul>
      )}
    </section>
  )
}

function QueueItem({ ticket }: { ticket: Ticket }) {
  return (
    <li>
      <div style={{ minWidth: 0 }}>
        <Link className="queue-title" to={`/tickets/${ticket.id}`}>{ticket.title}</Link>
        <div className="queue-meta">
          <span className="mono">{ticket.ticketNumber}</span>
          <StatusIndicator status={ticket.status} />
          <span>Updated {formatRelative(ticket.updatedAt)}</span>
        </div>
      </div>
      <PriorityBadge priority={ticket.priority} />
    </li>
  )
}

function LedgerCard({ title, base = {} }: { title: string; base?: TicketFilters }) {
  return (
    <section className="card" aria-labelledby="ledger-title">
      <div className="card-header">
        <div>
          <h2 id="ledger-title">{title}</h2>
          <p>Where every ticket stands in its lifecycle.</p>
        </div>
      </div>
      <div className="card-body">
        <StatusLedger
          base={base}
          linkTo={(status: TicketStatus) => ticketsLink({
            status,
            ...(base.organizationId ? { organizationId: String(base.organizationId) } : {}),
          })}
        />
      </div>
    </section>
  )
}

function CustomerDashboard({ user }: { user: User }) {
  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Hello, {user.name.split(' ')[0]}</h1>
          <p>Your support tickets at {user.organization?.name}.</p>
        </div>
        <Link to="/tickets/new" className="button">Report a problem</Link>
      </header>
      <div className="grid-halves">
        <Queue
          title="Waiting for you"
          description="Resolved tickets. Close them if the fix worked, or reopen them if not."
          filters={{ status: ['RESOLVED'], sort: 'updatedAt,desc' }}
          empty="Nothing needs your confirmation."
          viewAll={ticketsLink({ status: 'RESOLVED' })}
        />
        <Queue
          title="Being worked on"
          description="Open tickets, newest first."
          filters={{ status: ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED'] }}
          empty="You have no open tickets."
          viewAll={ticketsLink({ status: ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED'] })}
        />
      </div>
      <LedgerCard title="All your tickets" />
    </div>
  )
}

function AgentDashboard({ user }: { user: User }) {
  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Your queue</h1>
          <p>Tickets assigned to you, {user.name.split(' ')[0]}.</p>
        </div>
      </header>
      <div className="grid-halves">
        <Queue
          title="Ready to start"
          description="Assigned or reopened, highest priority first."
          filters={{ status: ['ASSIGNED', 'REOPENED'], sort: 'priority,desc' }}
          empty="Nothing waiting to be started."
          viewAll={ticketsLink({ status: ['ASSIGNED', 'REOPENED'], sort: 'priority,desc' })}
        />
        <Queue
          title="In progress"
          description="Work you've started, highest priority first."
          filters={{ status: ['IN_PROGRESS'], sort: 'priority,desc' }}
          empty="No tickets in progress."
          viewAll={ticketsLink({ status: 'IN_PROGRESS', sort: 'priority,desc' })}
        />
      </div>
      <LedgerCard title="Everything assigned to you" />
    </div>
  )
}

function AgentWorkload({ organizationId }: { organizationId: number }) {
  const agents = useQuery({
    queryKey: queryKeys.userList({ role: 'SUPPORT_AGENT', active: true, size: 20 }),
    queryFn: () => usersApi.list({ role: 'SUPPORT_AGENT', active: true, size: 20 }),
  })
  const loads = useQuery({
    queryKey: [...queryKeys.tickets, 'workload', agents.data?.content.map((agent) => agent.id)],
    enabled: !!agents.data,
    queryFn: async ({ signal }) => Promise.all((agents.data?.content ?? []).map(async (agent) => ({
      agent,
      open: (await ticketsApi.list({
        organizationId, assignedAgentId: agent.id, status: ['ASSIGNED', 'IN_PROGRESS', 'REOPENED'], size: 1,
      }, signal)).totalElements,
    }))),
  })

  const max = Math.max(1, ...(loads.data ?? []).map((load) => load.open))

  return (
    <section className="card" aria-labelledby="workload-title">
      <div className="card-header">
        <div>
          <h2 id="workload-title">Agent workload</h2>
          <p>Active tickets per agent.</p>
        </div>
        <Link className="button button-quiet button-small" to="/users?role=SUPPORT_AGENT">Manage agents</Link>
      </div>
      {(agents.isPending || (agents.data && loads.isPending)) && <SkeletonRows rows={3} />}
      {(agents.isError || loads.isError) && <ErrorState error={agents.error ?? loads.error} />}
      {agents.data?.content.length === 0 && (
        <EmptyState title="No support agents yet" action={<Link className="button button-secondary" to="/users?role=SUPPORT_AGENT">Add an agent</Link>}>
          Tickets can only be assigned once your organization has an agent.
        </EmptyState>
      )}
      {loads.data && loads.data.length > 0 && (
        <ul className="workload">
          {[...loads.data].sort((a, b) => b.open - a.open).map(({ agent, open }) => (
            <li key={agent.id}>
              <Link to={ticketsLink({ assignedAgentId: String(agent.id) })}>{agent.name}</Link>
              <div className="workload-track" aria-hidden="true">
                <div className="workload-fill" style={{ width: `${(open / max) * 100}%` }} />
              </div>
              <span className="mono" style={{ textAlign: 'right' }} aria-label={`${open} active tickets`}>{open}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function OrgAdminDashboard({ user }: { user: User }) {
  const organizationId = user.organization?.id ?? 0
  const organization = useQuery({
    queryKey: queryKeys.organization(organizationId),
    queryFn: () => organizationsApi.get(organizationId),
    enabled: organizationId > 0,
  })
  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>{user.organization?.name ?? 'Your organization'}</h1>
          <p>
            {organization.data
              ? `${organization.data.industry} · ${organization.data.domain} · ${organization.data.companyEmail}`
              : 'Organization overview'}
          </p>
        </div>
        <Link to="/tickets?unassigned=true" className="button">Assign tickets</Link>
      </header>
      <LedgerCard title="Tickets by status" base={{ organizationId }} />
      <div className="grid-halves">
        <Queue
          title="Needs an agent"
          description="Unassigned tickets, highest priority first."
          filters={{ unassigned: true, status: ['OPEN', 'REOPENED'], sort: 'priority,desc' }}
          empty="Every ticket has an agent."
          viewAll={ticketsLink({ unassigned: 'true', sort: 'priority,desc' })}
        />
        <AgentWorkload organizationId={organizationId} />
      </div>
    </div>
  )
}

function SuperAdminDashboard() {
  const organizations = useQuery({
    queryKey: queryKeys.organizationList({ size: 1 }),
    queryFn: () => organizationsApi.list({ size: 1 }),
  })
  const users = useQuery({
    queryKey: queryKeys.userList({ size: 1 }),
    queryFn: () => usersApi.list({ size: 1 }),
  })
  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>System overview</h1>
          <p>
            {organizations.data && users.data
              ? `${organizations.data.totalElements} organizations · ${users.data.totalElements} people`
              : 'All organizations'}
          </p>
        </div>
        <div className="row">
          <Link to="/organizations" className="button button-secondary">Organizations</Link>
          <Link to="/users" className="button">People</Link>
        </div>
      </header>
      <LedgerCard title="Tickets across all organizations" />
      <Queue
        title="Most urgent open tickets"
        description="Across every organization, highest priority first. Read-only for super admins."
        filters={{ status: ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED'], sort: 'priority,desc' }}
        empty="No open tickets anywhere."
        viewAll={ticketsLink({ status: ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED'], sort: 'priority,desc' })}
      />
    </div>
  )
}

export function DashboardPage() {
  const user = useCurrentUser()
  useDocumentTitle(user.role === 'CUSTOMER' || user.role === 'SUPPORT_AGENT' ? 'Dashboard' : 'Overview')
  switch (user.role) {
    case 'CUSTOMER':
      return <CustomerDashboard user={user} />
    case 'SUPPORT_AGENT':
      return <AgentDashboard user={user} />
    case 'ORG_ADMIN':
      return <OrgAdminDashboard user={user} />
    case 'SUPER_ADMIN':
      return <SuperAdminDashboard />
  }
}
