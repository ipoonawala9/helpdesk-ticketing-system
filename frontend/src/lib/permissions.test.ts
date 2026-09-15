import { describe, expect, it } from 'vitest'
import type { Role, Ticket, TicketStatus, User } from '../api/types'
import { rolePermissions, ticketPermissions } from './permissions'

const org = { id: 7, name: 'Acme' }
const otherOrg = { id: 8, name: 'Globex' }

function user(id: number, role: Role, organization = org): User {
  return { id, name: `User ${id}`, email: `u${id}@x.test`, role, phoneNumber: null, active: true, organization }
}

const customer = user(1, 'CUSTOMER')
const otherCustomer = user(2, 'CUSTOMER')
const agent = user(20, 'SUPPORT_AGENT')
const otherAgent = user(21, 'SUPPORT_AGENT')
const admin = user(10, 'ORG_ADMIN')
const foreignAdmin = user(11, 'ORG_ADMIN', otherOrg)
const superAdmin = user(99, 'SUPER_ADMIN', null as never)

function ticket(status: TicketStatus, assigned: User | null = agent): Ticket {
  const summary = (u: User) => ({ id: u.id, name: u.name, email: u.email, role: u.role })
  return {
    id: 42, ticketNumber: 'HD-2026-000042', title: 't', description: 'd', status, priority: 'MEDIUM',
    category: 'HARDWARE', customer: summary(customer), assignedAgent: assigned ? summary(assigned) : null,
    organization: org, reopenCount: 0, createdAt: '2026-09-14T10:00:00', updatedAt: '2026-09-14T10:00:00',
    resolvedAt: null, closedAt: null,
  }
}

const everyone = [customer, otherCustomer, agent, otherAgent, admin, foreignAdmin, superAdmin]
const allowed = (check: (u: User, t: Ticket) => boolean, t: Ticket) => everyone.filter((u) => check(u, t))

describe('ticket permissions mirror the backend rules', () => {
  it('only the ticket customer may edit, and not once closed', () => {
    expect(allowed(ticketPermissions.canEdit, ticket('IN_PROGRESS'))).toEqual([customer])
    expect(allowed(ticketPermissions.canEdit, ticket('CLOSED'))).toEqual([])
  })

  it('only an admin of the ticket organization may assign, and only before resolution', () => {
    for (const status of ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED'] as TicketStatus[]) {
      expect(allowed(ticketPermissions.canAssign, ticket(status))).toEqual([admin])
    }
    expect(allowed(ticketPermissions.canAssign, ticket('RESOLVED'))).toEqual([])
    expect(allowed(ticketPermissions.canAssign, ticket('CLOSED'))).toEqual([])
  })

  it('only the assigned agent may start (from ASSIGNED or REOPENED) and resolve (from IN_PROGRESS)', () => {
    expect(allowed(ticketPermissions.canStart, ticket('ASSIGNED'))).toEqual([agent])
    expect(allowed(ticketPermissions.canStart, ticket('REOPENED'))).toEqual([agent])
    expect(allowed(ticketPermissions.canStart, ticket('IN_PROGRESS'))).toEqual([])
    expect(allowed(ticketPermissions.canResolve, ticket('IN_PROGRESS'))).toEqual([agent])
    expect(allowed(ticketPermissions.canResolve, ticket('ASSIGNED'))).toEqual([])
    expect(allowed(ticketPermissions.canStart, ticket('OPEN', null))).toEqual([])
  })

  it('the customer may reopen resolved or closed tickets; customer or own-org admin may close resolved ones', () => {
    expect(allowed(ticketPermissions.canReopen, ticket('RESOLVED'))).toEqual([customer])
    expect(allowed(ticketPermissions.canReopen, ticket('CLOSED'))).toEqual([customer])
    expect(allowed(ticketPermissions.canReopen, ticket('IN_PROGRESS'))).toEqual([])
    expect(allowed(ticketPermissions.canClose, ticket('RESOLVED'))).toEqual([customer, admin])
    expect(allowed(ticketPermissions.canClose, ticket('IN_PROGRESS'))).toEqual([])
  })

  it('messages: super admins cannot read; only the customer and assigned agent may post, not on closed tickets', () => {
    expect(everyone.filter((u) => ticketPermissions.canReadMessages(u))).not.toContain(superAdmin)
    expect(allowed(ticketPermissions.canPostMessage, ticket('RESOLVED'))).toEqual([customer, agent])
    expect(allowed(ticketPermissions.canPostMessage, ticket('CLOSED'))).toEqual([])
  })

  it('only an admin of the ticket organization may delete', () => {
    expect(allowed(ticketPermissions.canDelete, ticket('OPEN'))).toEqual([admin])
  })
})

describe('role permissions', () => {
  it('org admins create agents and customers; super admins any role; others none', () => {
    expect(rolePermissions.creatableRoles('ORG_ADMIN')).toEqual(['SUPPORT_AGENT', 'CUSTOMER'])
    expect(rolePermissions.creatableRoles('SUPER_ADMIN')).toContain('ORG_ADMIN')
    expect(rolePermissions.creatableRoles('SUPPORT_AGENT')).toEqual([])
    expect(rolePermissions.creatableRoles('CUSTOMER')).toEqual([])
  })
})
