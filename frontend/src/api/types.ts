// Mirrors of the backend's response and request DTOs. Enum types are string
// unions because the backend serialises enums by name.

export type Role = 'SUPER_ADMIN' | 'ORG_ADMIN' | 'SUPPORT_AGENT' | 'CUSTOMER'

export type TicketStatus = 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED' | 'REOPENED' | 'CLOSED'
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type TicketCategory = 'HARDWARE' | 'SOFTWARE' | 'BILLING' | 'ACCOUNT' | 'NETWORK' | 'SECURITY' | 'OTHER'

export const TICKET_STATUSES: TicketStatus[] = ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED', 'RESOLVED', 'CLOSED']
export const TICKET_PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
export const TICKET_CATEGORIES: TicketCategory[] = [
  'HARDWARE', 'SOFTWARE', 'BILLING', 'ACCOUNT', 'NETWORK', 'SECURITY', 'OTHER',
]

export interface OrganizationSummary {
  id: number
  name: string
}

export interface Organization extends OrganizationSummary {
  companyEmail: string
  domain: string
  industry: string
}

export interface UserSummary {
  id: number
  name: string
  email: string
  role: Role
}

export interface User extends UserSummary {
  phoneNumber: string | null
  active: boolean
  organization: OrganizationSummary | null
}

/** Timestamps are ISO local date-times without a zone, always in UTC. */
export type UtcDateTime = string

export interface Ticket {
  id: number
  ticketNumber: string
  title: string
  description: string
  status: TicketStatus
  priority: TicketPriority
  category: TicketCategory
  customer: UserSummary
  assignedAgent: UserSummary | null
  organization: OrganizationSummary
  reopenCount: number
  createdAt: UtcDateTime
  updatedAt: UtcDateTime
  resolvedAt: UtcDateTime | null
  closedAt: UtcDateTime | null
}

export interface Message {
  id: number
  ticketId: number
  /** Null when the author's account has been deleted. */
  sender: UserSummary | null
  content: string
  createdAt: UtcDateTime
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface LoginResponse {
  accessToken: string
  tokenType: 'Bearer'
  /** ISO instant with a zone. */
  expiresAt: string
  user: User
}

export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors?: Record<string, string>
}

export interface TicketFilters {
  status?: TicketStatus[]
  priority?: TicketPriority[]
  category?: TicketCategory[]
  customerId?: number
  assignedAgentId?: number
  unassigned?: boolean
  organizationId?: number
  q?: string
  page?: number
  size?: number
  sort?: string
}

export interface UserFilters {
  role?: Role
  organizationId?: number
  active?: boolean
  q?: string
  page?: number
  size?: number
  sort?: string
}

export interface CreateTicketInput {
  title: string
  description: string
  category: TicketCategory
}

export interface CreateUserInput {
  name: string
  email: string
  password: string
  phoneNumber?: string
  role: Role
  organizationId?: number
}

export interface CreateOrganizationInput {
  name: string
  companyEmail: string
  domain: string
  industry: string
}
