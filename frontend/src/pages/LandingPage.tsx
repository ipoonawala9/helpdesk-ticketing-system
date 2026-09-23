import { Link, Navigate } from 'react-router'
import type { Ticket } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { BrandMark } from '../components/Brand'
import { TicketStub } from '../components/TicketBits'
import { DEMO_ACCOUNTS, demoEnabled } from '../lib/demo'
import { useDocumentTitle } from '../lib/hooks'

// An illustration of a ticket, not data from the API.
const SAMPLE: Ticket = {
  id: 0, ticketNumber: 'HD-2026-000128', title: 'VPN drops every few minutes', description: '',
  status: 'IN_PROGRESS', priority: 'HIGH', category: 'NETWORK',
  customer: { id: 0, name: 'Priya Shah', email: '', role: 'CUSTOMER' },
  assignedAgent: { id: 0, name: 'Sam Ortiz', email: '', role: 'SUPPORT_AGENT' },
  organization: { id: 0, name: 'Northwind' }, reopenCount: 0,
  createdAt: '2026-09-16T08:40:00', updatedAt: '2026-09-16T09:12:00', resolvedAt: null, closedAt: null,
}

const LIFECYCLE = [
  ['Opened', 'A customer reports a problem. The description sets the priority; nobody has to triage it by hand.'],
  ['Assigned', 'An administrator routes it to an agent, and can hand it over at any point before it is resolved.'],
  ['In progress', 'The agent starts work. Customer and agent talk on the ticket itself, not over email.'],
  ['Resolved', 'The agent marks it fixed. The customer decides whether it really is.'],
  ['Closed', 'The customer confirms, or an administrator closes it after a grace period.'],
] as const

const ROLES = [
  ['Customers', 'Report problems and watch them move. Message the agent working on the ticket, confirm the fix, or reopen it if the problem comes back.'],
  ['Support agents', 'A queue of exactly the tickets assigned to you, ordered by priority. Start work, reply, resolve.'],
  ['Organization admins', 'See every ticket in the organization, assign and reassign agents, and manage the people who can sign in.'],
  ['Super admins', 'Create organizations, manage accounts, and view tickets across the whole system.'],
] as const

const CAPABILITIES = [
  ['Priority without triage', 'Priority is read from the ticket: its category, wording and how often it has been reopened. An outage is critical; a how-to question is not.'],
  ['One conversation per ticket', 'Messages live on the ticket, so the history is in one place. Administrators can read along; nobody else can.'],
  ['Organizations stay separate', 'Every query is scoped to the organization. A ticket from another company is answered as if it does not exist.'],
  ['Find anything', 'Search by number, title or description, filter by status, priority, category or agent, and sort by urgency.'],
  ['Access that can be revoked', 'Deactivate someone and they are signed out immediately, everywhere.'],
  ['A documented API', 'Every endpoint is described in OpenAPI and browsable in Swagger UI.'],
] as const

export function LandingPage() {
  useDocumentTitle('')
  const { user } = useAuth()

  // Someone already signed in wants the app, not the pitch.
  if (user) return <Navigate to="/dashboard" replace />

  return (
    <div className="landing">
      <header className="landing-bar">
        <span className="brand"><BrandMark size={24} /> HelpDesk</span>
        <nav className="row" aria-label="Page sections">
          <a className="landing-link" href="#how">How it works</a>
          <a className="landing-link" href="#roles">Who it is for</a>
          <Link className="button" to="/login">Sign in</Link>
        </nav>
      </header>

      <section className="landing-hero">
        <div className="stack" style={{ gap: 'var(--space-5)' }}>
          <span className="landing-eyebrow">Support desk for multi-organization teams</span>
          <h1 className="landing-title">Every issue gets a ticket. Every ticket gets seen through.</h1>
          <p className="landing-lead">
            HelpDesk gives each customer's problem a number, an owner and a visible position in its lifecycle,
            from the moment it is reported until the customer agrees it is fixed.
          </p>
          <div className="row">
            <Link className="button button-lg" to="/login">Sign in</Link>
            <a className="button button-secondary button-lg" href="#how">See how it works</a>
          </div>
          <p className="field-hint">
            Accounts are created by your organization's administrator. There is no public sign-up.
          </p>
        </div>
        <div className="landing-stub"><TicketStub ticket={SAMPLE} heading="p" /></div>
      </section>

      <section id="how" className="landing-section">
        <div className="landing-section-head">
          <h2>How a ticket moves</h2>
          <p>Five stops. Everyone involved can see which one a ticket is at, and who it is waiting on.</p>
        </div>
        <ol className="landing-steps">
          {LIFECYCLE.map(([title, description], index) => (
            <li key={title}>
              <span className="landing-step-mark" aria-hidden="true" />
              <h3>{title}</h3>
              <p>{description}</p>
              <span className="landing-step-number mono" aria-hidden="true">{String(index + 1).padStart(2, '0')}</span>
            </li>
          ))}
        </ol>
      </section>

      <section id="roles" className="landing-section landing-section-dark">
        <div className="landing-section-head">
          <h2>Four ways in</h2>
          <p>What you can do is decided by your account, not by how you sign in.</p>
        </div>
        <div className="landing-grid">
          {ROLES.map(([title, description]) => (
            <article key={title} className="landing-card">
              <h3>{title}</h3>
              <p>{description}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="landing-section">
        <div className="landing-section-head">
          <h2>What's inside</h2>
        </div>
        <div className="landing-grid">
          {CAPABILITIES.map(([title, description]) => (
            <article key={title} className="landing-card landing-card-plain">
              <h3>{title}</h3>
              <p>{description}</p>
            </article>
          ))}
        </div>
      </section>

      {demoEnabled && (
        <section id="demo" className="landing-section landing-demo">
          <div className="landing-section-head">
            <h2>Try it</h2>
            <p>This demo holds sample data. Pick a role and the sign-in form is filled in for you.</p>
          </div>
          <div className="landing-grid">
            {DEMO_ACCOUNTS.map((account) => (
              <Link key={account.role} className="landing-card landing-demo-card" to="/login" state={{ demo: account.role }}>
                <h3>{account.label}</h3>
                <p>{account.summary}</p>
                <span className="landing-demo-cta">Open as {account.label.toLowerCase()} →</span>
              </Link>
            ))}
          </div>
        </section>
      )}

      <footer className="landing-footer">
        <div>
          <span className="brand" style={{ color: 'var(--ink)' }}><BrandMark size={20} /> HelpDesk</span>
          <p className="field-hint" style={{ marginTop: 'var(--space-2)' }}>
            Java 25 · Spring Boot 4 · PostgreSQL 17 · React · TypeScript
          </p>
        </div>
        <nav className="row" aria-label="Elsewhere">
          <a className="landing-link" href="https://github.com/ipoonawala9/helpdesk-ticketing-system">Source on GitHub</a>
          <Link className="landing-link" to="/login">Sign in</Link>
        </nav>
      </footer>
    </div>
  )
}
