import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ticketsApi } from '../api/endpoints'
import type { TicketFilters, TicketStatus } from '../api/types'
import { STATUS_LABEL } from '../lib/format'
import { queryKeys } from '../lib/queryKeys'
import { ErrorState, SkeletonRows } from './Feedback'

// Lifecycle order. Colours are one-hue steps, light to dark, because the
// statuses are ordered stages rather than unrelated categories.
const ORDER: TicketStatus[] = ['OPEN', 'ASSIGNED', 'REOPENED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']
const STAGE_COLOR: Record<TicketStatus, string> = {
  OPEN: 'var(--stage-0)',
  ASSIGNED: 'var(--stage-1)',
  REOPENED: 'var(--stage-2)',
  IN_PROGRESS: 'var(--stage-3)',
  RESOLVED: 'var(--stage-4)',
  CLOSED: 'var(--stage-5)',
}

/**
 * How the tickets in scope are spread across the lifecycle. The backend has
 * no statistics endpoint, so each count is the total of a one-row page.
 */
export function useStatusCounts(base: TicketFilters = {}) {
  return useQuery({
    queryKey: [...queryKeys.tickets, 'status-counts', base],
    queryFn: async ({ signal }) => {
      const pages = await Promise.all(
        ORDER.map((status) => ticketsApi.list({ ...base, status: [status], size: 1 }, signal)),
      )
      return ORDER.map((status, index) => ({ status, count: pages[index].totalElements }))
    },
  })
}

export function StatusLedger({ base = {}, linkTo }: { base?: TicketFilters; linkTo?: (status: TicketStatus) => string }) {
  const counts = useStatusCounts(base)

  if (counts.isPending) return <SkeletonRows rows={2} />
  if (counts.isError) return <ErrorState error={counts.error} onRetry={() => counts.refetch()} />

  const total = counts.data.reduce((sum, entry) => sum + entry.count, 0)
  const visible = counts.data.filter((entry) => entry.count > 0)

  return (
    <div className="ledger">
      {total === 0 ? (
        <div className="ledger-empty" aria-hidden="true" />
      ) : (
        <div className="ledger-bar" role="img" aria-label={`${total} tickets by status`}>
          {visible.map((entry) => (
            <span
              key={entry.status}
              style={{ flexGrow: entry.count, background: STAGE_COLOR[entry.status] }}
              title={`${STATUS_LABEL[entry.status]}: ${entry.count} of ${total}`}
            />
          ))}
        </div>
      )}
      <ul className="ledger-legend">
        {counts.data.map((entry) => {
          const content = (
            <>
              <span className="swatch" style={{ background: STAGE_COLOR[entry.status] }} aria-hidden="true" />
              <span className="ledger-name">{STATUS_LABEL[entry.status]}</span>
              <span className="ledger-count">{entry.count}</span>
            </>
          )
          return (
            <li key={entry.status}>
              {linkTo ? <Link to={linkTo(entry.status)} style={{ flex: 1 }}>{content}</Link> : content}
            </li>
          )
        })}
      </ul>
    </div>
  )
}
