import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ticketsApi, usersApi } from '../api/endpoints'
import {
  TICKET_CATEGORIES, TICKET_PRIORITIES, TICKET_STATUSES,
  type TicketCategory, type TicketFilters, type TicketPriority, type TicketStatus,
} from '../api/types'
import { useCurrentUser } from '../auth/AuthContext'
import { EmptyState, ErrorState, SkeletonRows } from '../components/Feedback'
import { SelectField, TextField } from '../components/Fields'
import { Pagination } from '../components/Pagination'
import { TicketTable } from '../components/TicketTable'
import { CATEGORY_LABEL, PRIORITY_LABEL, STATUS_LABEL } from '../lib/format'
import { useDebounced, useDocumentTitle } from '../lib/hooks'
import { queryKeys } from '../lib/queryKeys'

const TITLES = {
  CUSTOMER: ['My tickets', 'Everything you have reported.'],
  SUPPORT_AGENT: ['Assigned tickets', 'Tickets currently assigned to you.'],
  ORG_ADMIN: ['Tickets', 'Every ticket in your organization.'],
  SUPER_ADMIN: ['All tickets', 'Tickets across every organization. Read-only.'],
} as const

const SORTS = [
  ['createdAt,desc', 'Newest first'],
  ['updatedAt,desc', 'Recently updated'],
  ['priority,desc', 'Highest priority'],
  ['status,asc', 'Lifecycle order'],
  ['createdAt,asc', 'Oldest first'],
] as const

function numberParam(value: string | null) {
  return value && /^\d+$/.test(value) ? Number(value) : undefined
}

/** Filters live in the URL, so a filtered list can be bookmarked, shared and navigated back to. */
function readFilters(params: URLSearchParams): TicketFilters {
  return {
    status: params.getAll('status').filter((s): s is TicketStatus => TICKET_STATUSES.includes(s as TicketStatus)),
    priority: params.getAll('priority').filter((p): p is TicketPriority => TICKET_PRIORITIES.includes(p as TicketPriority)),
    category: params.getAll('category').filter((c): c is TicketCategory => TICKET_CATEGORIES.includes(c as TicketCategory)),
    customerId: numberParam(params.get('customerId')),
    assignedAgentId: numberParam(params.get('assignedAgentId')),
    organizationId: numberParam(params.get('organizationId')),
    unassigned: params.get('unassigned') === 'true' || undefined,
    q: params.get('q') ?? undefined,
    sort: params.get('sort') ?? 'createdAt,desc',
    page: numberParam(params.get('page')) ?? 0,
    size: 20,
  }
}

export function TicketsPage() {
  const user = useCurrentUser()
  const [title, subtitle] = TITLES[user.role]
  useDocumentTitle(title)
  const [params, setParams] = useSearchParams()
  const filters = readFilters(params)
  const [search, setSearch] = useState(filters.q ?? '')
  const debouncedSearch = useDebounced(search)

  function update(changes: Record<string, string | string[] | undefined>) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([key, value]) => {
      next.delete(key)
      ;(Array.isArray(value) ? value : value ? [value] : []).forEach((item) => next.append(key, item))
    })
    if (!('page' in changes)) next.delete('page')
    setParams(next, { replace: true })
  }

  useEffect(() => {
    if ((filters.q ?? '') !== debouncedSearch) update({ q: debouncedSearch || undefined })
    // Only react to the debounced search text.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch])

  const tickets = useQuery({
    queryKey: queryKeys.ticketList(filters),
    queryFn: ({ signal }) => ticketsApi.list(filters, signal),
    placeholderData: keepPreviousData,
  })

  const isAdmin = user.role === 'ORG_ADMIN' || user.role === 'SUPER_ADMIN'
  const agents = useQuery({
    queryKey: queryKeys.userList({ role: 'SUPPORT_AGENT', size: 100 }),
    queryFn: () => usersApi.list({ role: 'SUPPORT_AGENT', size: 100, organizationId: filters.organizationId }),
    enabled: user.role === 'ORG_ADMIN',
  })

  const toggleStatus = (status: TicketStatus) => {
    const current = filters.status ?? []
    update({ status: current.includes(status) ? current.filter((s) => s !== status) : [...current, status] })
  }

  const hasFilters = !!(filters.status?.length || filters.priority?.length || filters.category?.length
    || filters.customerId || filters.assignedAgentId || filters.unassigned || filters.q)

  // Scoping to one organization comes from where the viewer arrived, not from
  // the filter bar, so clearing keeps it.
  const clearFilters = () => {
    setSearch('')
    setParams(filters.organizationId ? { organizationId: String(filters.organizationId) } : {}, { replace: true })
  }

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </div>
        {user.role === 'CUSTOMER' && <Link to="/tickets/new" className="button">Report a problem</Link>}
      </header>

      <section className="card">
        <div className="filters" role="search">
          <TextField
            className="field-grow"
            label="Search"
            type="search"
            placeholder="Ticket number, title or description"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            maxLength={100}
          />
          <SelectField label="Priority" value={filters.priority?.[0] ?? ''} onChange={(e) => update({ priority: e.target.value || undefined })}>
            <option value="">Any priority</option>
            {TICKET_PRIORITIES.map((p) => <option key={p} value={p}>{PRIORITY_LABEL[p]}</option>)}
          </SelectField>
          <SelectField label="Category" value={filters.category?.[0] ?? ''} onChange={(e) => update({ category: e.target.value || undefined })}>
            <option value="">Any category</option>
            {TICKET_CATEGORIES.map((c) => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}
          </SelectField>
          {user.role === 'ORG_ADMIN' && (
            <SelectField
              label="Agent"
              value={filters.unassigned ? 'none' : filters.assignedAgentId ? String(filters.assignedAgentId) : ''}
              onChange={(e) => update(e.target.value === 'none'
                ? { unassigned: 'true', assignedAgentId: undefined }
                : { unassigned: undefined, assignedAgentId: e.target.value || undefined })}
            >
              <option value="">Any agent</option>
              <option value="none">Unassigned</option>
              {agents.data?.content.map((agent) => <option key={agent.id} value={agent.id}>{agent.name}</option>)}
            </SelectField>
          )}
          <SelectField label="Sort" value={filters.sort} onChange={(e) => update({ sort: e.target.value })}>
            {SORTS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </SelectField>
          <fieldset className="filters-status">
            <legend className="field-label">Status</legend>
            <div className="chip-group">
              {TICKET_STATUSES.map((status) => (
                <button key={status} type="button" className="chip" aria-pressed={filters.status?.includes(status) ?? false} onClick={() => toggleStatus(status)}>
                  {STATUS_LABEL[status]}
                </button>
              ))}
              {hasFilters && (
                <button type="button" className="button button-quiet button-small filters-clear" onClick={clearFilters}>
                  Clear filters
                </button>
              )}
            </div>
          </fieldset>
        </div>

        {tickets.isPending && <SkeletonRows rows={6} />}
        {tickets.isError && <ErrorState error={tickets.error} onRetry={() => tickets.refetch()} />}
        {tickets.data && tickets.data.content.length === 0 && (
          hasFilters
            ? (
              <EmptyState
                title="No tickets match these filters"
                action={<button type="button" className="button button-secondary" onClick={clearFilters}>Clear filters</button>}
              >
                Try removing a filter, or search for something else.
              </EmptyState>
            )
            : user.role === 'CUSTOMER'
              ? <EmptyState title="You haven't reported anything yet" action={<Link to="/tickets/new" className="button">Report a problem</Link>}>When something isn't working, open a ticket and an agent will pick it up.</EmptyState>
              : <EmptyState title={isAdmin ? 'No tickets yet' : 'Nothing is assigned to you'}>{isAdmin ? 'Tickets appear here as customers report problems.' : 'Tickets appear here when an administrator assigns them to you.'}</EmptyState>
        )}
        {tickets.data && tickets.data.content.length > 0 && (
          <div style={{ opacity: tickets.isPlaceholderData ? 0.6 : 1, transition: 'opacity 120ms' }}>
            <TicketTable tickets={tickets.data.content} viewerRole={user.role} />
            <Pagination page={tickets.data} noun="tickets" onPage={(page) => update({ page: page > 0 ? String(page) : undefined })} />
          </div>
        )}
      </section>
    </div>
  )
}
