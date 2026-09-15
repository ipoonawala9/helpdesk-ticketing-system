import { Link } from 'react-router'
import type { Role, Ticket } from '../api/types'
import { CATEGORY_LABEL, formatDateTime, formatRelative } from '../lib/format'
import { PriorityBadge, StatusIndicator } from './TicketBits'

/** Tickets as a table on wide screens and stacked rows on narrow ones. */
export function TicketTable({ tickets, viewerRole }: { tickets: Ticket[]; viewerRole: Role }) {
  const showCustomer = viewerRole !== 'CUSTOMER'
  const showOrganization = viewerRole === 'SUPER_ADMIN'
  return (
    <div className="table-wrap">
      <table className="table table-responsive">
        <thead>
          <tr>
            <th scope="col">Ticket</th>
            <th scope="col">Status</th>
            <th scope="col">Priority</th>
            {showCustomer && <th scope="col">Customer</th>}
            <th scope="col">Agent</th>
            <th scope="col">Updated</th>
          </tr>
        </thead>
        <tbody>
          {tickets.map((ticket) => (
            <tr key={ticket.id}>
              <td data-wide>
                <Link className="row-link" to={`/tickets/${ticket.id}`}>{ticket.title}</Link>
                <span className="cell-sub">
                  <span className="mono">{ticket.ticketNumber}</span> · {CATEGORY_LABEL[ticket.category]}
                  {showOrganization && <> · {ticket.organization.name}</>}
                </span>
              </td>
              <td><StatusIndicator status={ticket.status} /></td>
              <td><PriorityBadge priority={ticket.priority} /></td>
              {showCustomer && <td data-hide-mobile>{ticket.customer.name}</td>}
              <td data-hide-mobile>{ticket.assignedAgent?.name ?? <span className="muted">Unassigned</span>}</td>
              <td data-hide-mobile>
                <time dateTime={ticket.updatedAt} title={formatDateTime(ticket.updatedAt)}>{formatRelative(ticket.updatedAt)}</time>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
