import type { Role, TicketCategory, TicketPriority, TicketStatus, UtcDateTime } from '../api/types'

/** The API sends zone-less timestamps that are always UTC. */
export function parseUtc(value: UtcDateTime): Date {
  return new Date(/[zZ]|[+-]\d\d:?\d\d$/.test(value) ? value : `${value}Z`)
}

const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

export function formatDateTime(value: UtcDateTime | null | undefined): string {
  return value ? dateTime.format(parseUtc(value)) : '—'
}

const relative = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })
const UNITS: Array<[Intl.RelativeTimeFormatUnit, number]> = [
  ['year', 31_536_000],
  ['month', 2_592_000],
  ['week', 604_800],
  ['day', 86_400],
  ['hour', 3_600],
  ['minute', 60],
]

export function formatRelative(value: UtcDateTime | null | undefined, now: number = Date.now()): string {
  if (!value) return '—'
  const seconds = Math.round((parseUtc(value).getTime() - now) / 1000)
  for (const [unit, size] of UNITS) {
    if (Math.abs(seconds) >= size) return relative.format(Math.round(seconds / size), unit)
  }
  return 'just now'
}

export const STATUS_LABEL: Record<TicketStatus, string> = {
  OPEN: 'Open',
  ASSIGNED: 'Assigned',
  IN_PROGRESS: 'In progress',
  RESOLVED: 'Resolved',
  REOPENED: 'Reopened',
  CLOSED: 'Closed',
}

export const PRIORITY_LABEL: Record<TicketPriority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
}

export const CATEGORY_LABEL: Record<TicketCategory, string> = {
  HARDWARE: 'Hardware',
  SOFTWARE: 'Software',
  BILLING: 'Billing',
  ACCOUNT: 'Account',
  NETWORK: 'Network',
  SECURITY: 'Security',
  OTHER: 'Other',
}

export const CATEGORY_HINT: Record<TicketCategory, string> = {
  HARDWARE: 'Laptops, printers, phones and other devices',
  SOFTWARE: 'Applications that crash, error or behave unexpectedly',
  BILLING: 'Invoices, charges and payments',
  ACCOUNT: 'Signing in, access and permissions',
  NETWORK: 'Internet, Wi-Fi and VPN',
  SECURITY: 'Suspicious emails, possible breaches, lost devices',
  OTHER: 'Anything else; describe it below',
}

export const ROLE_LABEL: Record<Role, string> = {
  SUPER_ADMIN: 'Super admin',
  ORG_ADMIN: 'Organization admin',
  SUPPORT_AGENT: 'Support agent',
  CUSTOMER: 'Customer',
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase() || '?'
}
