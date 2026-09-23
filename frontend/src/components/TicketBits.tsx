import type { Ticket, TicketPriority, TicketStatus } from '../api/types'
import { CATEGORY_LABEL, PRIORITY_LABEL, STATUS_LABEL, formatRelative } from '../lib/format'
import { LIFECYCLE_STOPS, lifecycleIndex } from '../lib/lifecycle'

const PRIORITY_LEVEL: Record<TicketPriority, number> = { LOW: 1, MEDIUM: 2, HIGH: 3, CRITICAL: 4 }

/** Priority as triage bars filled to its level, always with its name beside it. */
export function PriorityBadge({ priority }: { priority: TicketPriority }) {
  const level = PRIORITY_LEVEL[priority]
  return (
    <span className="priority" data-level={priority}>
      <span className="priority-bars" aria-hidden="true">
        {[1, 2, 3, 4].map((bar) => <span key={bar} data-on={bar <= level} />)}
      </span>
      <span className="priority-label">{PRIORITY_LABEL[priority]}</span>
    </span>
  )
}

/** Compact status for lists: a miniature punch strip and the status name. */
export function StatusIndicator({ status }: { status: TicketStatus }) {
  const index = lifecycleIndex(status)
  return (
    <span className="status">
      <span className="punch-mini" aria-hidden="true">
        {LIFECYCLE_STOPS.map((stop, stopIndex) => <span key={stop.key} data-passed={stopIndex <= index} />)}
      </span>
      <span>{STATUS_LABEL[status]}</span>
    </span>
  )
}

/** The ticket's lifecycle, with each stop it has reached punched through. */
export function PunchStrip({ ticket }: { ticket: Ticket }) {
  const index = lifecycleIndex(ticket.status)
  return (
    <ol className="punch" aria-label={`Status: ${STATUS_LABEL[ticket.status]}`}>
      {LIFECYCLE_STOPS.map((stop, stopIndex) => {
        const current = stopIndex === index
        const label = current && ticket.status === 'REOPENED' ? 'Reopened' : stop.label
        return (
          <li key={stop.key} data-passed={stopIndex < index} data-current={current} aria-current={current ? 'step' : undefined}>
            <span className="hole" aria-hidden="true" />
            <span className="punch-label">{label}</span>
          </li>
        )
      })}
    </ol>
  )
}

/**
 * The signature header of a ticket: an amber stub torn along a perforation.
 *
 * On a ticket's own page the title is the page heading; used as an
 * illustration elsewhere it must not claim the page's only h1.
 */
export function TicketStub({ ticket, heading = 'h1' }: { ticket: Ticket; heading?: 'h1' | 'p' }) {
  const Title = heading
  return (
    <div className="stub-frame">
      <article className="stub" aria-labelledby={`ticket-${ticket.id}-title`}>
        <div className="stub-band">
          <div>
            <div className="stub-eyebrow">Ticket</div>
            <div className="stub-number">{ticket.ticketNumber}</div>
          </div>
          <div className="stack" style={{ gap: 'var(--space-2)' }}>
            <span className="stub-eyebrow">{CATEGORY_LABEL[ticket.category]}</span>
            <PriorityBadge priority={ticket.priority} />
          </div>
        </div>
        <div className="stub-body">
          <div className="stack" style={{ gap: 'var(--space-2)' }}>
            <Title id={`ticket-${ticket.id}-title`} className="stub-title">{ticket.title}</Title>
            <div className="stub-meta">
              <span>Opened by {ticket.customer.name}</span>
              <span>{ticket.organization.name}</span>
              <span>Updated {formatRelative(ticket.updatedAt)}</span>
              {ticket.reopenCount > 0 && (
                <span>Reopened {ticket.reopenCount === 1 ? 'once' : `${ticket.reopenCount} times`}</span>
              )}
            </div>
          </div>
          <PunchStrip ticket={ticket} />
        </div>
      </article>
    </div>
  )
}
