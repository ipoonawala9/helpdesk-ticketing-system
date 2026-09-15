import type { Ticket, TicketStatus } from '../api/types'

/** The stations a ticket passes through, punched in order on its stub. */
export const LIFECYCLE_STOPS = [
  { key: 'OPEN', label: 'Opened' },
  { key: 'ASSIGNED', label: 'Assigned' },
  { key: 'IN_PROGRESS', label: 'In progress' },
  { key: 'RESOLVED', label: 'Resolved' },
  { key: 'CLOSED', label: 'Closed' },
] as const

const STOP_INDEX: Record<TicketStatus, number> = {
  OPEN: 0,
  ASSIGNED: 1,
  // A reopened ticket is back with its agent, waiting for work to start again.
  REOPENED: 1,
  IN_PROGRESS: 2,
  RESOLVED: 3,
  CLOSED: 4,
}

export function lifecycleIndex(status: TicketStatus): number {
  return STOP_INDEX[status]
}

/** A short sentence saying who the ticket is waiting on, addressing the viewer as "you" when it is them. */
export function waitingOn(ticket: Ticket, viewerId?: number): string {
  const agentIsViewer = ticket.assignedAgent?.id === viewerId
  const customerIsViewer = ticket.customer.id === viewerId
  const agent = agentIsViewer ? 'you' : ticket.assignedAgent?.name ?? 'the agent'
  const customer = customerIsViewer ? 'you' : ticket.customer.name
  const capitalise = (text: string) => text.charAt(0).toUpperCase() + text.slice(1)

  switch (ticket.status) {
    case 'OPEN':
      return 'Waiting for an administrator to assign an agent.'
    case 'ASSIGNED':
      return `Waiting for ${agent} to start work.`
    case 'REOPENED':
      return `Reopened. Waiting for ${agent} to pick it back up.`
    case 'IN_PROGRESS':
      return agentIsViewer ? "You're working on it." : `${capitalise(agent)} is working on it.`
    case 'RESOLVED':
      return customerIsViewer
        ? 'Resolved. Close it if the fix worked, or reopen it if not.'
        : `Resolved. Waiting for ${customer} to confirm the fix or reopen.`
    case 'CLOSED':
      return 'Closed.'
  }
}
