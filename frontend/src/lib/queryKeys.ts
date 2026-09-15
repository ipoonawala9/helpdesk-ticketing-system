import type { TicketFilters, UserFilters } from '../api/types'

export const queryKeys = {
  tickets: ['tickets'] as const,
  ticketList: (filters: TicketFilters) => ['tickets', 'list', filters] as const,
  ticket: (id: number) => ['tickets', 'detail', id] as const,
  messages: (ticketId: number) => ['tickets', 'messages', ticketId] as const,
  users: ['users'] as const,
  userList: (filters: UserFilters) => ['users', 'list', filters] as const,
  organizations: ['organizations'] as const,
  organizationList: (filters: object) => ['organizations', 'list', filters] as const,
  organization: (id: number) => ['organizations', 'detail', id] as const,
  me: ['me'] as const,
}
