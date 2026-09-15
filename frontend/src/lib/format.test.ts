import { describe, expect, it } from 'vitest'
import { formatRelative, initials, parseUtc } from './format'
import { lifecycleIndex, waitingOn } from './lifecycle'
import type { Ticket } from '../api/types'

describe('timestamps', () => {
  it('treats zone-less API timestamps as UTC', () => {
    expect(parseUtc('2026-09-14T12:00:00').toISOString()).toBe('2026-09-14T12:00:00.000Z')
    expect(parseUtc('2026-09-14T12:00:00.123456').getTime()).toBe(Date.UTC(2026, 8, 14, 12, 0, 0, 123))
  })

  it('leaves timestamps that already carry a zone alone', () => {
    expect(parseUtc('2026-09-14T12:00:00Z').toISOString()).toBe('2026-09-14T12:00:00.000Z')
    expect(parseUtc('2026-09-14T17:30:00+05:30').toISOString()).toBe('2026-09-14T12:00:00.000Z')
  })

  it('describes times relative to now', () => {
    const now = Date.UTC(2026, 8, 14, 12, 0, 0)
    expect(formatRelative('2026-09-14T11:59:40', now)).toBe('just now')
    expect(formatRelative('2026-09-14T09:00:00', now)).toMatch(/3 hours ago/)
    expect(formatRelative(null, now)).toBe('—')
  })
})

describe('lifecycle', () => {
  it('places a reopened ticket back at the assigned stop', () => {
    expect(lifecycleIndex('OPEN')).toBe(0)
    expect(lifecycleIndex('REOPENED')).toBe(lifecycleIndex('ASSIGNED'))
    expect(lifecycleIndex('CLOSED')).toBe(4)
  })
})

describe('initials', () => {
  it('uses first and last names', () => {
    expect(initials('Dana Maria Customer')).toBe('DC')
    expect(initials('sam')).toBe('S')
    expect(initials('  ')).toBe('?')
  })
})

describe('waitingOn', () => {
  const base = {
    id: 1, ticketNumber: 'HD-1', title: 't', description: 'd', priority: 'LOW', category: 'OTHER',
    customer: { id: 1, name: 'Grace Liu', email: '', role: 'CUSTOMER' },
    assignedAgent: { id: 3, name: 'Sam Ortiz', email: '', role: 'SUPPORT_AGENT' },
    organization: { id: 1, name: 'N' }, reopenCount: 0, createdAt: '', updatedAt: '', resolvedAt: null, closedAt: null,
  } as const
  const ticket = (status: Ticket['status']) => ({ ...base, status }) as Ticket

  it('names other people, and addresses the viewer as you', () => {
    expect(waitingOn(ticket('ASSIGNED'), 10)).toBe('Waiting for Sam Ortiz to start work.')
    expect(waitingOn(ticket('ASSIGNED'), 3)).toBe('Waiting for you to start work.')
    expect(waitingOn(ticket('IN_PROGRESS'), 3)).toBe("You're working on it.")
    expect(waitingOn(ticket('IN_PROGRESS'), 1)).toBe('Sam Ortiz is working on it.')
    expect(waitingOn(ticket('RESOLVED'), 1)).toBe('Resolved. Close it if the fix worked, or reopen it if not.')
    expect(waitingOn(ticket('RESOLVED'), 3)).toBe('Resolved. Waiting for Grace Liu to confirm the fix or reopen.')
  })
})
