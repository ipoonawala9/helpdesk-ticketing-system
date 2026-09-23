import type { Role } from '../api/types'

/**
 * Demo sign-in shortcuts for a public demo deployment holding throwaway data.
 * Off unless VITE_DEMO_MODE is "true" and a password is provided at build
 * time. Never enable this for a deployment with real data: anything in a
 * VITE_ variable is readable by every visitor.
 */
export interface DemoAccount {
  role: Role
  label: string
  email: string
  summary: string
}

const PASSWORD = import.meta.env.VITE_DEMO_PASSWORD ?? ''

export const demoEnabled = import.meta.env.VITE_DEMO_MODE === 'true' && PASSWORD !== ''
export const demoPassword = PASSWORD

export const DEMO_ACCOUNTS: DemoAccount[] = [
  {
    role: 'CUSTOMER',
    label: 'Customer',
    email: import.meta.env.VITE_DEMO_CUSTOMER_EMAIL ?? 'priya.shah@northwind.test',
    summary: 'Report a problem, follow it, and close or reopen it.',
  },
  {
    role: 'SUPPORT_AGENT',
    label: 'Support agent',
    email: import.meta.env.VITE_DEMO_AGENT_EMAIL ?? 'sam.ortiz@northwind.test',
    summary: 'Work the queue: start, resolve, and reply to customers.',
  },
  {
    role: 'ORG_ADMIN',
    label: 'Organization admin',
    email: import.meta.env.VITE_DEMO_ORG_ADMIN_EMAIL ?? 'maya.chen@northwind.test',
    summary: 'Assign tickets, watch the queue, manage agents and customers.',
  },
  {
    role: 'SUPER_ADMIN',
    label: 'Super admin',
    email: import.meta.env.VITE_DEMO_SUPER_ADMIN_EMAIL ?? 'admin@helpdesk.local',
    summary: 'Organizations and people across the whole system.',
  },
]
