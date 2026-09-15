import type { Role, Ticket, TicketStatus, User } from '../api/types'

// Which controls to show. These mirror the backend's rules so users are not
// offered actions that will be refused, but they are not security: the
// backend checks every request independently.

const ASSIGNABLE: TicketStatus[] = ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED']
const STARTABLE: TicketStatus[] = ['ASSIGNED', 'REOPENED']

export function isTicketCustomer(user: User, ticket: Ticket) {
  return user.role === 'CUSTOMER' && ticket.customer.id === user.id
}

export function isAssignedAgent(user: User, ticket: Ticket) {
  return user.role === 'SUPPORT_AGENT' && ticket.assignedAgent?.id === user.id
}

export function isTicketOrgAdmin(user: User, ticket: Ticket) {
  return user.role === 'ORG_ADMIN' && user.organization?.id === ticket.organization.id
}

export const ticketPermissions = {
  /** The backend allows edits in any status; a closed ticket is reopened rather than edited. */
  canEdit: (user: User, ticket: Ticket) => isTicketCustomer(user, ticket) && ticket.status !== 'CLOSED',
  canAssign: (user: User, ticket: Ticket) => isTicketOrgAdmin(user, ticket) && ASSIGNABLE.includes(ticket.status),
  canStart: (user: User, ticket: Ticket) => isAssignedAgent(user, ticket) && STARTABLE.includes(ticket.status),
  canResolve: (user: User, ticket: Ticket) => isAssignedAgent(user, ticket) && ticket.status === 'IN_PROGRESS',
  /** A closed ticket past the reopen window is refused by the backend with an explanation. */
  canReopen: (user: User, ticket: Ticket) =>
    isTicketCustomer(user, ticket) && (ticket.status === 'RESOLVED' || ticket.status === 'CLOSED'),
  /** An admin closing before the customer's grace period is refused by the backend with the time it opens. */
  canClose: (user: User, ticket: Ticket) =>
    ticket.status === 'RESOLVED' && (isTicketCustomer(user, ticket) || isTicketOrgAdmin(user, ticket)),
  canDelete: (user: User, ticket: Ticket) => isTicketOrgAdmin(user, ticket),
  canReadMessages: (user: User) => user.role !== 'SUPER_ADMIN',
  canPostMessage: (user: User, ticket: Ticket) =>
    ticket.status !== 'CLOSED' && (isTicketCustomer(user, ticket) || isAssignedAgent(user, ticket)),
}

export const rolePermissions = {
  canCreateTickets: (role: Role) => role === 'CUSTOMER',
  canManageUsers: (role: Role) => role === 'ORG_ADMIN' || role === 'SUPER_ADMIN',
  canManageOrganizations: (role: Role) => role === 'SUPER_ADMIN',
  /** Roles an administrator of the given role may create. */
  creatableRoles: (role: Role): Role[] =>
    role === 'SUPER_ADMIN'
      ? ['ORG_ADMIN', 'SUPPORT_AGENT', 'CUSTOMER', 'SUPER_ADMIN']
      : role === 'ORG_ADMIN'
        ? ['SUPPORT_AGENT', 'CUSTOMER']
        : [],
}
