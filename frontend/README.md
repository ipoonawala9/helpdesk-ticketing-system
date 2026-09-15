# HelpDesk frontend

React + TypeScript + Vite single-page app for the HelpDesk API. It uses the real
backend for everything; there is no mock data.

## Run it locally

You need Node 22+ and a running backend. The quickest way is Docker Compose from
the repository root (see the main README):

```bash
docker compose up -d --build
```

```bash
npm install
npm run dev
```

Open http://localhost:5173. The Vite dev server forwards `/api` to
`http://localhost:8080`, so no CORS setup is needed locally. Point it elsewhere
with `DEV_API_PROXY_TARGET` in a `.env.local` file.

### Demo data

To try every role, fill an **empty local** backend with two organizations,
their people, tickets in every status and conversations:

```bash
API_URL=http://localhost:8080 \
SUPER_ADMIN_EMAIL=<bootstrap super admin email> \
SUPER_ADMIN_PASSWORD=<bootstrap super admin password> \
npm run seed:demo
```

Every demo account's password is `demo-password-123`:

| Role | Email |
|---|---|
| Organization admin | `maya.chen@northwind.test` |
| Support agent | `sam.ortiz@northwind.test` |
| Customer | `priya.shah@northwind.test` |

Demo data is for local development only; never run the script against production.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Development server with hot reload |
| `npm run build` | Type-check and build to `dist/` |
| `npm test` | Unit and component tests (Vitest, Testing Library) |
| `npm run typecheck` | TypeScript only |
| `npm run lint` | oxlint |
| `npm run seed:demo` | Demo data, see above |

## Configuration

Only public settings live in the frontend. Anything prefixed `VITE_` is compiled
into the browser bundle and readable by anyone.

| Variable | When | Meaning |
|---|---|---|
| `VITE_API_BASE_URL` | build time | The API's base URL, e.g. `https://helpdesk-ticketing-system-mi7f.onrender.com`. Leave empty in development. |
| `DEV_API_PROXY_TARGET` | development | Where the dev server forwards `/api`. Default `http://localhost:8080`. |

## How it is put together

```
src/
├── api/          client.ts (the only place that calls fetch), endpoints.ts, types.ts
├── auth/         session storage, AuthProvider, route guards
├── components/   app shell, ticket stub and punch strip, tables, dialogs, form fields
├── lib/          permissions, lifecycle, formatting, query keys, toasts
├── pages/        one file per screen
└── styles/       tokens.css (design tokens) and app.css
```

- **API client.** Every request goes through `api/client.ts`, which adds the
  access token, parses the backend's standard error body into an `ApiError`, and
  reports a `401` on an authenticated request so the session ends.
- **Server state** is cached with TanStack Query and invalidated after every
  change. Ticket conversations refresh every 20 seconds while open.
- **Filters live in the URL**, so a filtered ticket list can be bookmarked and
  shared, and Back restores it.
- **Global errors.** A `401` signs the user out and shows "Your session expired"
  on the login page. A `403` is shown as a message wherever it happens.

### Authentication

The backend issues bearer tokens, not cookies. The session (token, expiry and
user) is kept in `sessionStorage`: it survives a reload, ends when the tab is
closed, and is never written to `localStorage`. The session also ends in the
tab at the token's expiry time. Because the token is readable by JavaScript,
the app renders all user content as text, never as HTML, and loads no
third-party scripts or fonts.

### Permissions are for the interface only

`lib/permissions.ts` mirrors the backend's rules to decide which buttons to
show, so people are not offered actions that would be refused. It is not
security: the backend checks every request and has its own tests for those
rules. `lib/permissions.test.ts` checks that this copy stays in step with them.

### Screens

| Role | Screens |
|---|---|
| Customer | Dashboard (waiting for you, being worked on), My tickets, Report a problem, Ticket with edit, close, reopen and conversation, Profile |
| Support agent | Dashboard (ready to start, in progress), Assigned tickets, Ticket with start work, resolve and conversation, Profile |
| Organization admin | Overview (status breakdown, unassigned tickets, agent workload), Tickets with search and filters, assign and reassign, delete, Agents & customers, Profile |
| Super admin | Overview across organizations, Organizations, People, All tickets (read-only), Profile |

### Design

"Service counter": graphite and cool paper, with amber ticket cardstock used in
one place. Every ticket is shown as a **stub**: its number on an amber band,
torn along a perforation from its details, with a **punch strip** below that
punches each lifecycle stop the ticket has passed. Type is Archivo (headings),
Public Sans (text) and IBM Plex Mono (ticket numbers and times), all bundled
with the app. The status breakdown uses a single-hue ramp validated for
distinct steps and contrast, because statuses are ordered stages.
