#!/usr/bin/env node
/**
 * Fills a LOCAL HelpDesk backend with demo data through its public API:
 * two organizations with admins, agents and customers, tickets in every
 * lifecycle status, and conversations. For local development and demos only.
 *
 *   API_URL=http://localhost:8080 \
 *   SUPER_ADMIN_EMAIL=admin@helpdesk.local SUPER_ADMIN_PASSWORD=... \
 *   npm run seed:demo
 *
 * Every demo account uses the password in DEMO_PASSWORD (default below).
 * Run it once against an empty database; it does not deduplicate.
 */

const API = (process.env.API_URL ?? 'http://localhost:8080').replace(/\/+$/, '')
const SUPER_EMAIL = process.env.SUPER_ADMIN_EMAIL
const SUPER_PASSWORD = process.env.SUPER_ADMIN_PASSWORD
const DEMO_PASSWORD = process.env.DEMO_PASSWORD ?? 'demo-password-123'

if (!SUPER_EMAIL || !SUPER_PASSWORD) {
  console.error('Set SUPER_ADMIN_EMAIL and SUPER_ADMIN_PASSWORD to the bootstrap super admin.')
  process.exit(1)
}

async function call(method, path, token, body) {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  if (!response.ok) throw new Error(`${method} ${path} -> ${response.status}: ${text}`)
  return text ? JSON.parse(text) : undefined
}

const login = async (email, password = DEMO_PASSWORD) =>
  (await call('POST', '/api/auth/login', null, { email, password })).accessToken

const superToken = await login(SUPER_EMAIL, SUPER_PASSWORD)

async function organization(name, domain, industry) {
  return call('POST', '/api/organizations', superToken, { name, companyEmail: `support@${domain}`, domain, industry })
}

async function person(token, name, role, domain, organizationId) {
  const email = `${name.toLowerCase().replace(/[^a-z]+/g, '.')}@${domain}`
  const user = await call('POST', '/api/users', token, { name, email, password: DEMO_PASSWORD, role, organizationId })
  return { ...user, token: await login(email) }
}

async function ticket(customer, title, category, description) {
  return call('POST', '/api/tickets', customer.token, { title, category, description })
}

const say = (ticketId, who, content) => call('POST', `/api/tickets/${ticketId}/messages`, who.token, { content })
const act = (ticketId, who, action, body) => call('POST', `/api/tickets/${ticketId}/${action}`, who.token, body)

async function seedOrganization({ name, domain, industry, admin, agents, customers, tickets }) {
  const org = await organization(name, domain, industry)
  const adminUser = await person(superToken, admin, 'ORG_ADMIN', domain, org.id)
  const agentUsers = []
  for (const agent of agents) agentUsers.push(await person(adminUser.token, agent, 'SUPPORT_AGENT', domain))
  const customerUsers = []
  for (const customer of customers) customerUsers.push(await person(adminUser.token, customer, 'CUSTOMER', domain))

  for (const spec of tickets) {
    const customer = customerUsers[spec.customer]
    const agent = agentUsers[spec.agent ?? 0]
    const created = await ticket(customer, spec.title, spec.category, spec.description)
    if (spec.note) await say(created.id, customer, spec.note)
    if (spec.until === 'OPEN') continue
    await act(created.id, adminUser, 'assign', { agentId: agent.id })
    if (spec.until === 'ASSIGNED') continue
    await act(created.id, agent, 'start')
    if (spec.reply) await say(created.id, agent, spec.reply)
    if (spec.until === 'IN_PROGRESS') continue
    await act(created.id, agent, 'resolve')
    if (spec.until === 'RESOLVED') continue
    if (spec.until === 'REOPENED') {
      await act(created.id, customer, 'reopen')
      await say(created.id, customer, 'Still happening this morning, unfortunately.')
      continue
    }
    await act(created.id, customer, 'close')
  }
  return { org, adminUser }
}

const northwind = await seedOrganization({
  name: 'Northwind Traders', domain: 'northwind.test', industry: 'Logistics',
  admin: 'Maya Chen',
  agents: ['Sam Ortiz', 'Aisha Khan', 'Leo Marsh'],
  customers: ['Priya Shah', 'Tom Becker', 'Grace Liu'],
  tickets: [
    { customer: 0, until: 'IN_PROGRESS', agent: 0, title: 'VPN drops every few minutes', category: 'NETWORK',
      description: 'Since yesterday the VPN disconnects every 5 minutes from the warehouse office. It is blocking our shipping labels.',
      note: 'Happens on both Wi-Fi and cable.', reply: "Thanks Priya, I'm checking the gateway logs now." },
    { customer: 1, until: 'OPEN', title: 'Invoice shows the wrong billing address', category: 'BILLING',
      description: 'The September invoice lists our old Hamburg address. No rush, but accounting needs it fixed before month end.' },
    { customer: 2, until: 'OPEN', title: 'Suspicious email asking for payroll login', category: 'SECURITY',
      description: 'Three people got an email that looks like our payroll portal asking them to sign in. Possible phishing.' },
    { customer: 0, until: 'RESOLVED', agent: 1, title: 'Label printer in bay 4 jams on every job', category: 'HARDWARE',
      description: 'The Zebra printer jams after two labels. We cleaned the rollers already.',
      reply: 'Replaced the platen roller. Can you run a test batch?' },
    { customer: 1, until: 'REOPENED', agent: 1, title: "Can't log in to the route planner", category: 'ACCOUNT',
      description: "I can't log in since the password reset. It says my account is locked.", reply: 'Unlocked your account, try now.' },
    { customer: 2, until: 'CLOSED', agent: 2, title: 'How do I export the monthly delivery report?', category: 'SOFTWARE',
      description: 'Question: where is the export button for the monthly delivery report?', reply: "It's under Reports, then the download icon." },
    { customer: 0, until: 'ASSIGNED', agent: 2, title: 'Warehouse outage: scanners offline', category: 'NETWORK',
      description: 'All handheld scanners in the east warehouse lost connection. Production down for picking.' },
    { customer: 2, until: 'ASSIGNED', agent: 0, title: 'New starter needs a laptop', category: 'HARDWARE',
      description: 'We have a new dispatcher starting Monday who needs a laptop and accounts set up.' },
  ],
})

await seedOrganization({
  name: 'Globex Health', domain: 'globex.test', industry: 'Healthcare',
  admin: 'Daniel Price',
  agents: ['Nora Quinn'],
  customers: ['Elena Rossi'],
  tickets: [
    { customer: 0, until: 'IN_PROGRESS', agent: 0, title: 'Appointment system crashes on save', category: 'SOFTWARE',
      description: 'The scheduling app crashes whenever reception saves a recurring appointment.', reply: 'Reproduced it, working on a fix.' },
    { customer: 0, until: 'OPEN', title: 'Billing export missing insurance codes', category: 'BILLING',
      description: 'The weekly billing export no longer includes insurance codes.' },
  ],
})

console.log(`Seeded demo data at ${API}. Every demo account's password is "${DEMO_PASSWORD}". Try:`)
console.log('  Organization admin  maya.chen@northwind.test')
console.log('  Support agent       sam.ortiz@northwind.test')
console.log('  Customer            priya.shah@northwind.test')
console.log(`  Super admin         ${SUPER_EMAIL}`)
void northwind
