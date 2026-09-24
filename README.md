# HelpDesk Ticketing System

A RESTful backend API for managing support tickets across multiple organizations. Built with Spring Boot 4, Java 25, and PostgreSQL.

> **Live API:** https://helpdesk-ticketing-system-mi7f.onrender.com

---

## Table of Contents

- [Problem Statement](#problem-statement)
- [Problem Solution](#problem-solution)
- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Domain Model](#domain-model)
  - [Organization](#organization)
  - [User](#user)
  - [Ticket](#ticket)
  - [Message](#message)
- [Enums](#enums)
- [Automatic Priority](#automatic-priority)
- [Authentication & Authorization](#authentication--authorization)
- [Multi-Tenant Isolation](#multi-tenant-isolation)
- [API Reference](#api-reference)
  - [Lists: paging, sorting, filtering and search](#lists-paging-sorting-filtering-and-search)
  - [Organizations](#organizations-api)
  - [Users](#users-api)
  - [Tickets](#tickets-api)
  - [Messages](#messages-api)
- [DTOs](#dtos)
- [Database Constraints and Indexes](#database-constraints-and-indexes)
- [Exception Handling](#exception-handling)
- [Testing](#testing)
- [Configuration](#configuration)
- [Frontend](#frontend)
- [Running Locally](#running-locally)
- [Running with Docker](#running-with-docker)
- [Postman Screenshots](#postman-screenshots)

---

## Problem Statement

In organizations with multiple teams and departments, managing customer support requests manually leads to inefficiencies such as lost or untracked issues, lack of accountability, and no structured visibility into the status of a request. There is no centralized system to record, assign, and monitor support tickets across different organizational units, resulting in delayed resolutions and poor customer experience.

---

## Problem Solution

The HelpDesk Ticketing System addresses these challenges by providing a centralized, multi-tenant RESTful backend API that structures the entire support workflow. Every support request is captured as a ticket, automatically linked to the raising customer's organization, and tracked through a well-defined lifecycle — Open, Assigned, In Progress, Resolved, Reopened, and Closed. Each ticket is assigned a unique human-readable identifier, categorized by type and priority, and timestamped at every stage. The system supports multiple roles — Super Admin, Org Admin, Support Agent, and Customer — ensuring clear accountability at every level. Built on Spring Boot 4, Java 25, and PostgreSQL, with full Docker containerization, the system is scalable, portable, and production-ready.

---

## Overview

The HelpDesk Ticketing System is a multi-tenant support platform where:

- **Organizations** are the top-level tenants
- **Users** belong to an organization and can have one of four roles
- **Tickets** are raised by customers, automatically inherit the customer's organization, and track the full lifecycle of a support issue from `OPEN` through to `CLOSED`

Ticket numbers are auto-generated in the format `HD-2026-XXXXXX` using the database-generated ID.

Controllers never accept or return JPA entities. Every request is bound to a
validated request record and every response is built by an explicit mapper, so
passwords, Hibernate proxy fields and circular references cannot reach a client.

Every request except login is authenticated with a JWT bearer token, and the
acting user is always taken from that token, never from a request field. Each
role can only do what its job needs, and rules that depend on a specific
ticket, such as "only this ticket's customer", are enforced on the server.

Organizations are isolated from each other. Every lookup is scoped in the
database query to what the caller may see, and data outside that scope is
answered exactly as if it did not exist.

Ticket priority is always decided by the server from the ticket's category,
wording and reopen count; clients cannot set it.

Each ticket carries its own conversation. The customer and the assigned agent
exchange messages on the ticket, and administrators of the ticket's
organization can read along.

Ticket status never changes through a generic update. Each transition is a
named business action in `TicketWorkflowService` with its own rules:

| Action | Actor | Transition |
|---|---|---|
| Assign | `ORG_ADMIN` of the ticket's organization | `OPEN` / `ASSIGNED` / `IN_PROGRESS` / `REOPENED` → `ASSIGNED` |
| Start work | the ticket's assigned `SUPPORT_AGENT` | `ASSIGNED` / `REOPENED` → `IN_PROGRESS` |
| Resolve | the ticket's assigned `SUPPORT_AGENT` | `IN_PROGRESS` → `RESOLVED` (sets `resolvedAt`) |
| Close | the ticket's `CUSTOMER` at any time, or its `ORG_ADMIN` 3 hours after resolution | `RESOLVED` → `CLOSED` (sets `closedAt`) |
| Reopen | the ticket's `CUSTOMER` | `RESOLVED`, or `CLOSED` within 7 days → `REOPENED` |

The happy path is `OPEN` → `ASSIGNED` → `IN_PROGRESS` → `RESOLVED` → `CLOSED`.
A reopened ticket goes back to its agent, who starts work on it again.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.0.6 |
| Persistence | Spring Data JPA (Hibernate) |
| Database | PostgreSQL |
| Validation | Jakarta Bean Validation |
| Security | Spring Security 7, OAuth2 Resource Server (JWT, HS256), BCrypt |
| Boilerplate reduction | Lombok |
| API Docs | springdoc-openapi (OpenAPI 3, Swagger UI) |
| Build tool | Maven (Maven Wrapper included) |
| Frontend | React 19, TypeScript, Vite, React Router, TanStack Query, Vitest |
| Containerization | Docker (eclipse-temurin:25-jdk) |
| Testing | JUnit 5, Mockito, AssertJ, MockMvc, Spring Security Test, H2 (in-memory), Testcontainers (PostgreSQL 17) |

---

## Project Structure

```
helpdesk-ticketing-system/
├── src/
│   ├── main/
│   │   ├── java/com/ibrahim/helpdesk/
│   │   │   ├── HelpDeskApplication.java          # Entry point
│   │   │   ├── common/
│   │   │   │   ├── openapi/OpenApiConfig.java    # bearer auth scheme, standard error responses
│   │   │   │   ├── paging/PageRequests.java      # validated page/size/sort, safe LIKE patterns
│   │   │   │   └── paging/PageResponse.java      # JSON page envelope
│   │   │   ├── exception/
│   │   │   │   ├── ApiErrorResponse.java         # single error shape
│   │   │   │   ├── BusinessRuleException.java
│   │   │   │   ├── EmailAlreadyInUseException.java
│   │   │   │   ├── ForbiddenOperationException.java
│   │   │   │   ├── GlobalExceptionHandler.java   # @RestControllerAdvice
│   │   │   │   ├── InvalidTicketStateException.java
│   │   │   │   ├── OrganizationNotFoundException.java
│   │   │   │   ├── TicketNotFoundException.java
│   │   │   │   └── UserNotFoundException.java
│   │   │   ├── security/
│   │   │   │   ├── auth/AuthController.java              # POST /api/auth/login, GET /api/auth/me
│   │   │   │   ├── auth/AuthService.java
│   │   │   │   ├── auth/CurrentUserId.java               # injects the authenticated user's id
│   │   │   │   ├── auth/EmailUserDetailsService.java
│   │   │   │   ├── auth/InvalidCredentialsException.java
│   │   │   │   ├── bootstrap/BootstrapProperties.java
│   │   │   │   ├── bootstrap/SecurityStartupTasks.java   # hashes legacy passwords, creates first super admin
│   │   │   │   ├── config/SecurityConfig.java            # filter chain, JWT encoder/decoder, password encoder
│   │   │   │   ├── dto/LoginRequest.java
│   │   │   │   ├── dto/LoginResponse.java
│   │   │   │   ├── jwt/DatabaseUserJwtAuthenticationConverter.java
│   │   │   │   ├── jwt/JwtProperties.java
│   │   │   │   └── jwt/JwtTokenService.java
│   │   │   ├── message/
│   │   │   │   ├── controller/MessageController.java
│   │   │   │   ├── dto/MessageResponse.java
│   │   │   │   ├── dto/PostMessageRequest.java
│   │   │   │   ├── entity/Message.java
│   │   │   │   ├── mapper/MessageMapper.java
│   │   │   │   ├── repository/MessageRepository.java
│   │   │   │   └── service/MessageService.java
│   │   │   ├── organization/
│   │   │   │   ├── controller/OrganizationController.java
│   │   │   │   ├── dto/CreateOrganizationRequest.java
│   │   │   │   ├── dto/OrganizationResponse.java
│   │   │   │   ├── dto/OrganizationSummaryResponse.java
│   │   │   │   ├── entity/Organization.java
│   │   │   │   ├── mapper/OrganizationMapper.java
│   │   │   │   ├── repository/OrganizationRepository.java
│   │   │   │   └── service/OrganizationService.java
│   │   │   ├── user/
│   │   │   │   ├── controller/UserController.java
│   │   │   │   ├── dto/CreateUserRequest.java
│   │   │   │   ├── dto/UserResponse.java
│   │   │   │   ├── dto/UserSummaryResponse.java
│   │   │   │   ├── entity/User.java
│   │   │   │   ├── entity/UserRole.java
│   │   │   │   ├── mapper/UserMapper.java
│   │   │   │   ├── repository/UserRepository.java
│   │   │   │   ├── repository/UserSpecifications.java
│   │   │   │   └── service/UserService.java
│   │   │   └── ticket/
│   │   │       ├── config/TicketWorkflowConfig.java      # Clock bean
│   │   │       ├── config/TicketWorkflowProperties.java  # helpdesk.tickets.*
│   │   │       ├── controller/TicketController.java
│   │   │       ├── dto/AssignTicketRequest.java
│   │   │       ├── dto/CreateTicketRequest.java
│   │   │       ├── dto/TicketResponse.java
│   │   │       ├── dto/TicketSearchCriteria.java
│   │   │       ├── dto/UpdateTicketRequest.java
│   │   │       ├── entity/Ticket.java
│   │   │       ├── entity/TicketCategory.java
│   │   │       ├── entity/TicketPriority.java
│   │   │       ├── entity/TicketStatus.java
│   │   │       ├── mapper/TicketMapper.java
│   │   │       ├── priority/PriorityInput.java
│   │   │       ├── priority/RuleBasedTicketPriorityPolicy.java  # the rules
│   │   │       ├── priority/TicketPriorityBackfill.java         # fills missing priorities on startup
│   │   │       ├── priority/TicketPriorityPolicy.java           # replaceable interface
│   │   │       ├── repository/TicketRepository.java
│   │   │       ├── repository/TicketSpecifications.java  # tenant scope and list filters
│   │   │       ├── service/TicketParticipants.java     # who is customer / agent / admin of a ticket
│   │   │       ├── service/TicketService.java          # CRUD
│   │   │       └── service/TicketWorkflowService.java  # status transitions
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       ├── java/com/ibrahim/helpdesk/
│       │   ├── AccessControlIntegrationTest.java
│       │   ├── ApiIntegrationTestSupport.java     # shared end-to-end helpers, real tokens per user
│       │   ├── AuthIntegrationTest.java
│       │   ├── DatabaseConstraintsIntegrationTest.java
│       │   ├── MutableClock.java                  # test clock that can be advanced
│       │   ├── OpenApiIntegrationTest.java
│       │   ├── organization/controller/OrganizationControllerTest.java
│       │   ├── SecurityStartupTasksIntegrationTest.java
│       │   ├── TenantIsolationIntegrationTest.java
│       │   ├── security/jwt/JwtPropertiesTest.java
│       │   ├── support/WebSliceSecurity.java      # authenticated requests in @WebMvcTest slices
│       │   ├── HelpDeskApplicationTests.java
│       │   ├── exception/FrameworkErrorMappingTest.java
│       │   ├── message/controller/MessageControllerTest.java
│       │   ├── message/service/MessageServiceTest.java
│       │   ├── TicketApiIntegrationTest.java      # end-to-end, H2
│       │   ├── TicketAgentWorkflowIntegrationTest.java
│       │   ├── TicketAssignmentIntegrationTest.java
│       │   ├── TicketMessagingIntegrationTest.java
│       │   ├── TicketPriorityIntegrationTest.java
│       │   ├── TicketReopenCloseIntegrationTest.java
│       │   ├── TicketSearchIntegrationTest.java
│       │   ├── ticket/config/TicketWorkflowPropertiesTest.java
│       │   ├── ticket/controller/TicketControllerTest.java
│       │   ├── ticket/priority/RuleBasedTicketPriorityPolicyTest.java
│       │   ├── ticket/service/TicketServiceTest.java
│       │   ├── ticket/service/TicketWorkflowServiceTest.java
│       │   ├── user/controller/UserControllerTest.java
│       │   └── user/service/UserServiceTest.java
│       └── resources/
│           ├── application.properties             # in-memory H2 (default)
│           └── application-postgres.properties    # PostgreSQL 17 via Testcontainers
├── frontend/                                      # React + TypeScript + Vite app, see frontend/README.md
├── ss/                                            # Postman screenshots
├── Dockerfile
├── mvnw / mvnw.cmd
└── pom.xml
```

---

## Domain Model

### Organization

Table: `organizations`

| Field | Type | Constraints | Description |
|---|---|---|---|
| `id` | Long | PK, auto-generated | Unique identifier |
| `name` | String | `@NotBlank` | Organization name |
| `companyEmail` | String | `@NotBlank`, `@Email` | Official company email |
| `domain` | String | `@NotBlank` | Company domain (e.g. `acme.com`) |
| `industry` | String | `@NotBlank` | Industry sector |

---

### User

Table: `users`

| Field | Type | Constraints | Description |
|---|---|---|---|
| `id` | Long | PK, auto-generated | Unique identifier |
| `name` | String | — | Full name |
| `email` | String | — | Email address |
| `password` | String | `@JsonIgnore` | BCrypt hash, stored as `{bcrypt}...`; never the password itself and never returned |
| `phoneNumber` | String | — | Contact number |
| `role` | UserRole (enum) | — | Role within the system |
| `organization` | Organization | `@ManyToOne` | The org this user belongs to |
| `active` | Boolean | — | Whether the user account is active |

---

### Ticket

Table: `tickets`

| Field | Type | Description |
|---|---|---|
| `id` | Long | PK, auto-generated |
| `ticketNumber` | String | Human-readable ID, e.g. `HD-2026-000001` |
| `title` | String | Short summary of the issue |
| `description` | String | Detailed description of the issue |
| `status` | TicketStatus (enum) | Current lifecycle state |
| `priority` | TicketPriority (enum) | Urgency level, always calculated by the server (see [Automatic Priority](#automatic-priority)) |
| `category` | TicketCategory (enum) | Type of issue |
| `customer` | User | `@ManyToOne` — the user who raised the ticket |
| `assignedAgent` | User | `@ManyToOne`, nullable — the support agent handling it. If that agent's account is deleted the ticket is kept and becomes unassigned (`ON DELETE SET NULL`) |
| `organization` | Organization | `@ManyToOne` — auto-inherited from customer |
| `reopenCount` | Integer | Number of times ticket was reopened |
| `createdAt` | LocalDateTime | Ticket creation timestamp |
| `updatedAt` | LocalDateTime | Last update timestamp |
| `resolvedAt` | LocalDateTime | When ticket was resolved |
| `closedAt` | LocalDateTime | When ticket was closed |

### Message

Table: `messages`, indexed on `(ticket_id, created_at)` for reading a thread
in order.

| Field | Type | Description |
|---|---|---|
| `id` | Long | PK, auto-generated |
| `ticket` | Ticket | `@ManyToOne`, required. Deleted together with its ticket (`ON DELETE CASCADE`) |
| `sender` | User | `@ManyToOne`, nullable. If the user is deleted the message stays and `sender` becomes `NULL` |
| `content` | String | The message text, up to 5000 characters, trimmed |
| `createdAt` | LocalDateTime | When the message was posted |

---

## Enums

### UserRole

```
SUPER_ADMIN    – Platform-level administrator
ORG_ADMIN      – Administrator for a specific organization
SUPPORT_AGENT  – Handles and resolves tickets
CUSTOMER       – End user who raises tickets
```

### TicketStatus

```
OPEN        – Newly created, not yet assigned
ASSIGNED    – Assigned to a support agent
IN_PROGRESS – Agent is actively working on it
RESOLVED    – Agent has resolved the issue
REOPENED    – Customer reopened a resolved ticket
CLOSED      – Ticket is fully closed
```

### TicketPriority

```
LOW
MEDIUM
HIGH
CRITICAL
```

### TicketCategory

```
HARDWARE
SOFTWARE
BILLING
ACCOUNT
NETWORK
SECURITY
OTHER
```

---

## Automatic Priority

Clients never set priority. Any `priority` field in a request body is ignored.
The server calculates it whenever the inputs can change:

| When | Why |
|---|---|
| A ticket is created | first assessment |
| The customer edits title, description or category | priority is derived from exactly those fields, so it can go up or down |
| The customer reopens the ticket | the reopen count feeds into priority |
| The application starts | any ticket without a priority, i.e. one created before this feature, gets one; existing priorities are not touched |

Priority is a pure function of **category, title, description and reopen
count**, so the same ticket always gets the same priority.

### Rules

Applied in order. Title and description are matched case-insensitively on
whole words and phrases.

| # | Rule | Result |
|---|---|---|
| 1 | An **incident signal** appears: `breach`, `breached`, `data breach`, `hacked`, `ransomware`, `compromised`, `data loss`, `lost all data`, `outage`, `production down`, `system down`, `server down`, `site down`, `service down` | `CRITICAL`, and no further rules apply |
| 2 | **Category baseline**: `SECURITY` is `HIGH`; `OTHER` is `LOW`; `HARDWARE`, `SOFTWARE`, `BILLING`, `ACCOUNT`, `NETWORK` are `MEDIUM` | baseline |
| 3 | An **urgency signal** appears: `urgent`, `asap`, `emergency`, `blocked`, `cannot log in`, `can't log in`, `cannot login`, `can't login`, `unable to log in`, `unable to login`, `locked out`, `crash`, `crashes`, `crashed`, `crashing`, `malware`, `virus`, `phishing`, `payment failed`, `charged twice`, `double charged`, `overcharged`, `all users`, `everyone`, `entire team`, `whole team` | at least `HIGH` |
| 4 | Otherwise, a **low-urgency signal** appears: `question`, `how do i`, `how to`, `feature request`, `suggestion`, `whenever you can`, `when you have time`, `not urgent`, `non-urgent`, `no rush`, `not an emergency`, `low priority` | `LOW`, except `SECURITY` tickets, which stay `HIGH` |
| 5 | The ticket has been **reopened** | one level higher per reopen, but reopening alone never goes above `HIGH` |

Examples:

| Category | Wording | Reopens | Priority |
|---|---|---|---|
| `OTHER` | "How do I change the default font?" | 0 | `LOW` |
| `HARDWARE` | "The screen flickers now and then" | 0 | `MEDIUM` |
| `ACCOUNT` | "I can't log in since this morning" | 0 | `HIGH` |
| `NETWORK` | "Office outage, nobody can reach the internet" | 0 | `CRITICAL` |
| `SECURITY` | "Question about enabling 2FA, no rush" | 0 | `HIGH` |
| `OTHER` | "How do I change the default font?" | 2 | `HIGH` |

Design notes:
- **`CRITICAL` means an incident.** Only incident wording produces it. A ticket
  that keeps being reopened becomes `HIGH`, not `CRITICAL`.
- **Calm phrases are removed before urgency matching**, so "not urgent" does
  not count as "urgent". Beyond those listed phrases the rules do not
  understand negation: "not blocked" still counts as "blocked".
- **Replaceable.** The rules live in `RuleBasedTicketPriorityPolicy`, behind
  the `TicketPriorityPolicy` interface. A different implementation registered
  as the primary bean replaces them without touching any other code.

---

## Authentication & Authorization

### Logging in

```
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "email": "dana@acme.com",
  "password": "correct-horse"
}
```

Response `200 OK`:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresAt": "2026-09-14T13:00:00Z",
  "user": {
    "id": 1,
    "name": "Dana Customer",
    "email": "dana@acme.com",
    "phoneNumber": null,
    "role": "CUSTOMER",
    "active": true,
    "organization": { "id": 1, "name": "Acme Corp" }
  }
}
```

Send the token on every other request:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

`GET /api/auth/me` returns the authenticated user's own profile.

- Email matching ignores letter case and surrounding spaces.
- A wrong password, an unknown email and an inactive account all return the
  same `401` with `Invalid email or password`, so the response does not reveal
  which emails have accounts. A password hash comparison runs even for unknown
  emails, so response time does not reveal it either.
- Tokens are valid for 1 hour. There is no refresh token; the client logs in
  again.
- **Password guessing is limited.** After 5 wrong passwords for one email
  within 15 minutes, sign-in for that email returns `429 Too Many Requests`
  with a `Retry-After` header, without checking the password, until the 15
  minutes have passed. Unknown emails are limited the same way, so the limit
  reveals nothing. A correct password clears the count. The count is kept in
  memory per running instance. The trade-off of counting per email is that
  someone who knows an email can block that account for 15 minutes at a time.

### Changing your own password

```
POST /api/auth/password
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "currentPassword": "correct-horse",
  "newPassword": "a-much-longer-passphrase"
}
```

Response `204 No Content`. A wrong current password is a `400` with
`fieldErrors.currentPassword`, not a `401`, so clients do not mistake it for an
expired session. Wrong current passwords count towards the same guessing limit
as sign-in, so a stolen access token cannot be used to guess the password. The
new password must be 8 to 100 characters and different from the current one.
Tokens issued before the change stay valid until they expire.

### Forgotten passwords

```
POST /api/auth/forgot-password   {"email": "dana@acme.com"}
POST /api/auth/reset-password    {"token": "...", "newPassword": "..."}
```

Both return `204`. Asking for a link answers the same way whether or not the
address has an account, so the endpoint cannot be used to find out who is
registered.

- The link is single-use, expires after 30 minutes, and asking again
  invalidates the previous one.
- Only a SHA-256 of the token is stored, so a copy of the database cannot be
  used to take over an account.
- Requests are limited to 3 per address per window, so nobody can use it to
  flood an inbox.
- **Delivery:** with no mail server configured the link is written to the
  application log, which keeps the feature usable before email exists. Set
  `spring.mail.*` and the link is emailed instead. `FRONTEND_BASE_URL` decides
  the address in the link.

### Deactivating accounts

```
POST /api/users/{id}/deactivate
POST /api/users/{id}/activate
```

Both return the updated user. A deactivated user cannot sign in, and because
every request reloads the user, **any token they already hold stops working on
their next request**. Their tickets and messages are kept; reassign a
deactivated agent's open tickets.

- A `SUPER_ADMIN` can change any account except their own.
- An `ORG_ADMIN` can change agents and customers of their own organization;
  another admin is `403`, anyone outside their organization is `404`.
- Nobody can change their own account (`400`).

### How tokens are checked

Tokens are HS256-signed JWTs issued by this API. A token carries only the
issuer, the user's id as its subject, and its issue and expiry times: no role,
email or other personal data.

On every request the server verifies the signature, expiry and issuer, then
**loads the user from the database**. The request is rejected with `401` if
the user no longer exists or is inactive, and the user's authorities come from
their current role in the database rather than from the token. Deactivating a
user or changing their role therefore applies to their very next request, even
with a token issued earlier.

| Problem | Response |
|---|---|
| No `Authorization` header | `401`, `Authentication is required`, `WWW-Authenticate: Bearer` |
| Malformed, expired, wrongly signed, wrong issuer or unsigned token, or a token for a deleted or inactive user | `401`, `Invalid or expired access token`, `WWW-Authenticate: Bearer error="invalid_token"` |
| Valid token, but the role may not use the endpoint | `403`, `You do not have permission to perform this action` |
| Valid token and role, but the ticket, user or organization is outside the caller's scope | `404`, exactly as for an id that does not exist (see [Multi-Tenant Isolation](#multi-tenant-isolation)) |
| Valid token and role, the record is visible, but this particular action is not allowed | `403` with a specific reason, e.g. `Only organization administrators can assign tickets` |

### Who can do what

The acting user is **always** the authenticated user. No request body or query
parameter identifies who is acting; such a field, if sent, is ignored.

| Endpoint | Allowed roles | Additional rule |
|---|---|---|
| `POST /api/auth/login` | anyone | |
| `GET /api/auth/me` | any authenticated user | |
| `POST /api/auth/password` | any authenticated user | their own password, with the current one |
| `POST /api/organizations` | `SUPER_ADMIN` | |
| `GET /api/organizations` | `SUPER_ADMIN` | |
| `GET /api/organizations/{id}` | any | `SUPER_ADMIN`, or a member of that organization; otherwise `404` |
| `POST /api/users` | `SUPER_ADMIN`, `ORG_ADMIN` | `ORG_ADMIN`: only `CUSTOMER` and `SUPPORT_AGENT`, only in their own organization |
| `GET /api/users` | `SUPER_ADMIN`, `ORG_ADMIN` | `ORG_ADMIN`: own organization only |
| `GET /api/users/{id}` | any | yourself, `SUPER_ADMIN`, or an `ORG_ADMIN` of the user's organization; otherwise `404` |
| `POST /api/users/{id}/deactivate`, `/activate` | `SUPER_ADMIN`, `ORG_ADMIN` | not yourself; `ORG_ADMIN`: agents and customers of their own organization |
| `POST /api/tickets` | `CUSTOMER` | the ticket belongs to the authenticated customer |
| `GET /api/tickets` | any | each role sees only its scope, see [Multi-Tenant Isolation](#multi-tenant-isolation) |
| `GET /api/tickets/{id}` | any | within the caller's scope; otherwise `404` |
| `PUT /api/tickets/{id}` | `CUSTOMER` | the ticket's own customer |
| `DELETE /api/tickets/{id}` | `ORG_ADMIN` | of the ticket's organization |
| `POST /api/tickets/{id}/assign` | `ORG_ADMIN` | of the ticket's organization |
| `POST /api/tickets/{id}/start`, `/resolve` | `SUPPORT_AGENT` | the ticket's assigned agent |
| `POST /api/tickets/{id}/reopen` | `CUSTOMER` | the ticket's own customer |
| `POST /api/tickets/{id}/close` | `CUSTOMER`, `ORG_ADMIN` | the ticket's customer, or an admin of its organization after 3 hours |
| `POST /api/tickets/{id}/messages` | `CUSTOMER`, `SUPPORT_AGENT` | the ticket's customer or assigned agent |
| `GET /api/tickets/{id}/messages` | `CUSTOMER`, `SUPPORT_AGENT`, `ORG_ADMIN` | the ticket's customer, assigned agent, or an admin of its organization |
| `/v3/api-docs`, `/swagger-ui/**` | anyone | can be turned off with `API_DOCS_ENABLED=false` |
| `GET /actuator/health`, `/actuator/health/liveness`, `/readiness` | anyone | status only, no details; no other actuator endpoint is exposed |

Role checks are declared with `@PreAuthorize` on each controller method and
run before anything is looked up, so a role that may never call an endpoint
gets `403` without learning whether the id exists. Rules that depend on the
specific ticket, user or organization are enforced in the services.

There is no public sign-up: a `SUPER_ADMIN` creates organizations and their
`ORG_ADMIN`s, and each `ORG_ADMIN` creates their organization's agents and
customers.

### The first super admin

On startup, if `BOOTSTRAP_SUPER_ADMIN_EMAIL` and
`BOOTSTRAP_SUPER_ADMIN_PASSWORD` are set and no account with that email exists,
a `SUPER_ADMIN` is created with them. An existing account is never modified,
so the variables can stay set. If they are not set and no `SUPER_ADMIN` exists,
a warning is logged, since nobody would be able to create organizations.

### Passwords

Passwords are hashed with BCrypt through Spring Security's delegating encoder
and stored as `{bcrypt}...`, so the algorithm can be upgraded later without
invalidating existing passwords. On startup, any password still stored as plain
text, from before hashing was introduced, is hashed in place; those users log
in with the same password as before.

---

## Multi-Tenant Isolation

Each organization is a tenant. A user can only ever reach data inside their
scope:

| Role | Tickets | Users | Organizations |
|---|---|---|---|
| `CUSTOMER` | tickets they opened | themselves | their own |
| `SUPPORT_AGENT` | tickets currently assigned to them | themselves | their own |
| `ORG_ADMIN` | every ticket of their organization | users of their organization | their own |
| `SUPER_ADMIN` | every ticket, read-only | everyone | every organization |

### Scope is enforced in the query

Tickets and users are not loaded and then checked: the scope is part of the
database query (`findByIdAndCustomerId`, `findByIdAndAssignedAgentId`,
`findByIdAndOrganizationId`, and the matching list queries), so a record
outside it is never read at all. All ticket operations, including workflow
actions and messages, load their ticket through
`TicketService.findVisibleOrThrow`, and the action-specific rules are then
checked on top as a second layer.

### Out of scope looks exactly like missing

A ticket, user or organization outside the caller's scope returns `404` with
the same body as an id that was never created, e.g.
`Ticket with ID 42 not found`. Responses therefore never confirm that another
customer's ticket or another organization's user exists.

- An agent who is not assigned to a ticket, including one who was reassigned
  away from it, gets `404` for it and its conversation.
- Assigning an agent from another organization is `404`
  (`User with ID 7 not found`), not a hint that the user exists elsewhere.
- An org admin asking for `GET /api/users?organizationId=` of another
  organization gets `404`.

A `403` is only returned when the caller's role may never use the endpoint,
checked before any lookup, or when the record is visible but the specific
action is not allowed.

### Nothing moves between organizations

- A ticket's organization is always its customer's organization. Any
  `organizationId`, `customerId`, `assignedAgentId` or `status` sent when
  creating or editing a ticket is ignored.
- There is no endpoint that modifies a user, so a user's organization and role
  cannot be changed through the API. `PUT` and `PATCH /api/users/{id}` return
  `405`.
- An `ORG_ADMIN` can only create users in their own organization.

### Super admin

A `SUPER_ADMIN` has system-wide **read** access to tickets, users and
organizations, and manages organizations and users. They do not take part in
ticket work: assigning, closing and reading ticket conversations are refused
with `403`.

---

## API Reference

Base URL: `https://helpdesk-ticketing-system-mi7f.onrender.com`

Every endpoint below except login requires `Authorization: Bearer <token>`.
See [Authentication & Authorization](#authentication--authorization) for which
roles may call each one.

### Interactive documentation

The full contract is published as OpenAPI 3 and can be tried out in the
browser:

| | |
|---|---|
| Swagger UI | `/swagger-ui.html` |
| OpenAPI document | `/v3/api-docs` |

Both are public. In Swagger UI, call `POST /api/auth/login`, press
**Authorize**, and paste the `accessToken`; every other operation then sends it.
Each operation lists its possible error responses, all in the `ApiErrorResponse`
shape.

### Lists: paging, sorting, filtering and search

`GET /api/tickets`, `GET /api/users` and `GET /api/organizations` return one
page at a time:

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 57,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

| Parameter | Default | Rules |
|---|---|---|
| `page` | `0` | zero-based, `0` or greater |
| `size` | `20` | `1` to `100` |
| `sort` | per list, see below | `field` or `field,asc` / `field,desc`; only the listed fields are accepted |
| `q` | none | free-text search, at most 100 characters, case-insensitive; `%` and `_` are matched literally |

Rows with equal sort values are ordered by id, so paging never repeats or skips
a row. Invalid values are a `400` explaining what is allowed. Filters and search
only ever narrow the caller's [scope](#multi-tenant-isolation); they can never
widen it.

| List | Sort fields | Default sort | Search `q` matches | Filters |
|---|---|---|---|---|
| Tickets | `createdAt`, `updatedAt`, `priority`, `status`, `ticketNumber`, `title` | `createdAt,desc` | ticket number, title, description | `status`, `priority`, `category` (each repeatable or comma-separated), `customerId`, `assignedAgentId`, `unassigned=true`, `organizationId` |
| Users | `name`, `email`, `role` | `name,asc` | name, email | `role`, `active`, `organizationId` |
| Organizations | `name`, `domain`, `industry` | `name,asc` | name, domain | |

- Sorting by `priority` follows severity (`LOW` < `MEDIUM` < `HIGH` <
  `CRITICAL`) and by `status` follows the lifecycle (`OPEN`, `ASSIGNED`,
  `IN_PROGRESS`, `REOPENED`, `RESOLVED`, `CLOSED`), not alphabetical order.
- `organizationId` naming an organization other than the caller's own is
  `404` for everyone except a `SUPER_ADMIN`.
- `assignedAgentId` together with `unassigned=true` is a `400`.

Examples:

```
GET /api/tickets?status=OPEN&status=REOPENED&sort=priority,desc
GET /api/tickets?unassigned=true&category=HARDWARE,NETWORK
GET /api/tickets?q=printer&page=1&size=10
GET /api/users?role=SUPPORT_AGENT&active=true&q=sam
GET /api/organizations?q=acme
```

---

### Organizations API

#### Create Organization

`SUPER_ADMIN` only.

```
POST /api/organizations
Content-Type: application/json
```

Request body:
```json
{
  "name": "Acme Corp",
  "companyEmail": "contact@acme.com",
  "domain": "acme.com",
  "industry": "Technology"
}
```

All four fields are required. `companyEmail` must be a valid email address.

Response `201 Created`:
```json
{
  "id": 1,
  "name": "Acme Corp",
  "companyEmail": "contact@acme.com",
  "domain": "acme.com",
  "industry": "Technology"
}
```

---

#### List Organizations

`SUPER_ADMIN` only.

```
GET /api/organizations?q=acme&sort=name,asc&page=0&size=20
```

Response `200 OK`: a [page](#lists-paging-sorting-filtering-and-search) of
organization objects.

---

#### Get Organization by ID

A `SUPER_ADMIN`, or any member of the organization. For anyone else the
organization is reported as not found (`404`).

```
GET /api/organizations/{id}
```

Response `200 OK`:
```json
{
  "id": 1,
  "name": "Acme Corp",
  "companyEmail": "contact@acme.com",
  "domain": "acme.com",
  "industry": "Technology"
}
```

Response `404 Not Found`:
```json
{
  "timestamp": "2026-06-18T15:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Organization with id 99 not found",
  "path": "/api/organizations/99"
}
```

---

### Users API

#### Create User

```
POST /api/users
Content-Type: application/json
```

Request body:
```json
{
  "name": "John Doe",
  "email": "john@acme.com",
  "password": "secret123",
  "phoneNumber": "+1234567890",
  "role": "CUSTOMER",
  "organizationId": 1
}
```

- A `SUPER_ADMIN` can create any role. `organizationId` is required for every
  role except `SUPER_ADMIN`, and an unknown organization is a `404`.
- An `ORG_ADMIN` can only create `CUSTOMER` and `SUPPORT_AGENT` accounts, and
  always in their own organization. They may omit `organizationId`; naming a
  different organization, or asking for an admin role, is a `403`.
- Agents and customers cannot create users (`403`).
- The email is stored trimmed and lower-case and must not already belong to an
  account, in any letter case; a duplicate is a `409`.
- The request is bound to `CreateUserRequest`, not to the `User` entity, so `id`
  and `active` cannot be set by the caller. New users are always created active.
- The password is stored only as a BCrypt hash and is not a field on any
  response type.

Response `201 Created`:
```json
{
  "id": 1,
  "name": "John Doe",
  "email": "john@acme.com",
  "phoneNumber": "+1234567890",
  "role": "CUSTOMER",
  "active": true,
  "organization": {
    "id": 1,
    "name": "Acme Corp"
  }
}
```

Response `404 Not Found` (if org not found):
```json
{
  "timestamp": "2026-06-18T15:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Organization with id 99 not found",
  "path": "/api/users"
}
```

---

#### List Users

```
GET /api/users
GET /api/users?role=SUPPORT_AGENT&active=true
GET /api/users?organizationId=2&role=CUSTOMER&q=dana
```

`SUPER_ADMIN` and `ORG_ADMIN` only. Returns a
[page](#lists-paging-sorting-filtering-and-search) of users, ordered by name by
default.

- An `ORG_ADMIN` always gets their own organization's users. `organizationId`
  may be omitted or be their own; any other organization is `404`.
- A `SUPER_ADMIN` gets every user, or one organization's with
  `organizationId`.
- `role` narrows the list, e.g. `?role=SUPPORT_AGENT` for the agents a ticket
  can be assigned to.

Response `200 OK`: a page of user objects, without passwords. Organizations
are loaded in the same query, so the list does not issue one query per
organization.

---

#### Get User by ID

```
GET /api/users/{id}
```

Allowed for the user themselves, a `SUPER_ADMIN`, or an `ORG_ADMIN` of the
user's organization. For anyone else the user is reported as not found (`404`).

Response `200 OK`: single user object in the shape above.

Response `404 Not Found` if the user does not exist.

---

### Tickets API

#### Create Ticket

```
POST /api/tickets
Content-Type: application/json
```

Request body:
```json
{
  "title": "Cannot login to dashboard",
  "description": "Getting a 403 error when trying to log in since this morning.",
  "category": "ACCOUNT"
}
```

`CUSTOMER` only. Behavior on creation:
- The customer is the authenticated user
- Organization is auto-inherited from the customer
- Status is set to `OPEN`
- `assignedAgent` is set to `null`
- `reopenCount` is set to `0`
- `createdAt` and `updatedAt` are set to current timestamp
- Ticket is saved once to get the auto-generated ID, then `ticketNumber` is formatted as `HD-2026-XXXXXX` and saved again

Response `201 Created`:
```json
{
  "id": 1,
  "ticketNumber": "HD-2026-000001",
  "title": "Cannot login to dashboard",
  "description": "Getting a 403 error when trying to log in since this morning.",
  "status": "OPEN",
  "priority": "MEDIUM",
  "category": "ACCOUNT",
  "customer": {
    "id": 1,
    "name": "John Doe",
    "email": "john@acme.com",
    "role": "CUSTOMER"
  },
  "assignedAgent": null,
  "organization": {
    "id": 1,
    "name": "Acme Corp"
  },
  "reopenCount": 0,
  "createdAt": "2026-06-18T15:00:00",
  "updatedAt": "2026-06-18T15:00:00",
  "resolvedAt": null,
  "closedAt": null
}
```

The customer, assigned agent and organization are flattened into summary
objects. A ticket response never contains a password, a Hibernate proxy field
such as `hibernateLazyInitializer`, or a path back to another ticket.

Response `403 Forbidden` if the authenticated user is not a `CUSTOMER`.

---

#### List Tickets

```
GET /api/tickets
GET /api/tickets?status=OPEN&unassigned=true&sort=priority,desc
GET /api/tickets?q=HD-2026-000042
```

Every role can call this and gets a
[page](#lists-paging-sorting-filtering-and-search) of only the tickets in its
scope, newest first by default:

| Role | Tickets returned |
|---|---|
| `CUSTOMER` | tickets they opened |
| `SUPPORT_AGENT` | tickets currently assigned to them |
| `ORG_ADMIN` | every ticket of their organization |
| `SUPER_ADMIN` | every ticket |

The customer, assigned agent and organization of every ticket are loaded in
the same query, so the number of queries does not grow with the list.

Response `200 OK`: a page of ticket objects.

---

#### Get Ticket by ID

```
GET /api/tickets/{id}
```

Allowed for the ticket's customer, its current assigned agent, an `ORG_ADMIN`
of its organization, and a `SUPER_ADMIN`. For anyone else the ticket is
reported as not found (`404`), exactly like an id that does not exist.

Response `200 OK`: Single ticket object.

Response `404 Not Found`:
```json
{
  "timestamp": "2026-06-18T15:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Ticket with ID 99 not found",
  "path": "/api/tickets/99"
}
```

---

#### Update Ticket

```
PUT /api/tickets/{id}
Content-Type: application/json
```

Request body:
```json
{
  "title": "Updated title",
  "description": "Updated description with more details.",
  "category": "SOFTWARE"
}
```

- Updates `title`, `description`, `category`
- Automatically updates `updatedAt` to current timestamp

Only the customer who opened the ticket can edit it; to other customers the
ticket does not exist (`404`).
Only these three fields can be changed. Status, priority, assignment,
organization, ticket number, reopen count and the resolution and closure
timestamps are server-controlled and are not editable through this endpoint.

Response `200 OK`: Updated ticket object.

Response `404 Not Found`: standard error body.

---

#### Assign Ticket

```
POST /api/tickets/{id}/assign
Content-Type: application/json
```

Request body:
```json
{
  "agentId": 3
}
```

An organization administrator assigns the ticket to a support agent. On
success the ticket's `assignedAgent` is set, `status` becomes `ASSIGNED` and
`updatedAt` is refreshed. The administrator is the authenticated user.

Rules, checked in this order:

| # | Rule | Failure |
|---|---|---|
| 1 | Ticket exists within the admin's organization | `404 Not Found` |
| 2 | The authenticated user has role `ORG_ADMIN` and is active | `403 Forbidden` |
| 3 | Ticket status is `OPEN`, `ASSIGNED`, `IN_PROGRESS` or `REOPENED` | `409 Conflict` |
| 4 | Agent exists within the ticket's organization; an agent of another organization is treated as unknown | `404 Not Found` |
| 5 | Agent has role `SUPPORT_AGENT` and is active | `400 Bad Request` |

The admin is authorised before the agent is looked up, so a caller without
permission learns nothing about other users.

**Reassignment:**
- `ASSIGNED`, `IN_PROGRESS` and `REOPENED` tickets can be handed to a
  different agent. The ticket goes back to `ASSIGNED` and the new agent starts
  work on it, so a ticket is never stuck with an agent who has left or been
  deactivated.
- A `REOPENED` ticket keeps its agent unless an admin reassigns it.
- Assigning the agent who already holds the ticket is an idempotent no-op and
  changes nothing, including `updatedAt` and status, so an in-progress ticket
  is not sent back a step.
- `RESOLVED` and `CLOSED` tickets cannot be assigned.

Response `200 OK`: the updated ticket.

```json
{
  "id": 1,
  "ticketNumber": "HD-2026-000001",
  "status": "ASSIGNED",
  "assignedAgent": {
    "id": 3,
    "name": "Sam Agent",
    "email": "sam@acme.com",
    "role": "SUPPORT_AGENT"
  },
  "...": "remaining ticket fields unchanged"
}
```

---

#### Start Work

```
POST /api/tickets/{id}/start
```

No request body. The agent is the authenticated user.

The assigned agent begins working on the ticket. `status` moves from
`ASSIGNED` to `IN_PROGRESS` and `updatedAt` is refreshed.

#### Resolve Ticket

```
POST /api/tickets/{id}/resolve
```

No request body. The agent is the authenticated user.

The assigned agent marks the issue as fixed. `status` moves from `IN_PROGRESS`
to `RESOLVED`, and `resolvedAt` and `updatedAt` are set to the same timestamp.
The ticket is **not** closed; `closedAt` stays `null` until closure.

Rules for both actions, checked in this order:

| # | Rule | Failure |
|---|---|---|
| 1 | Ticket exists and is currently assigned to the authenticated agent | `404 Not Found` |
| 2 | The agent still has role `SUPPORT_AGENT` | `403 Forbidden` |
| 3 | Ticket is `ASSIGNED` or `REOPENED` (start), or `IN_PROGRESS` (resolve) | `409 Conflict` |

Notes:
- Role and active flag are re-checked on every request, because either may
  have changed since the ticket was assigned.
- An unassigned ticket fails rule 1, so nobody can start it.
- Repeating a transition, such as starting an `IN_PROGRESS` ticket or resolving
  a `RESOLVED` one, is a `409`, not a silent success. A repeated resolve never
  overwrites the original `resolvedAt`.
- Reassigning a ticket moves ownership: the previous agent can no longer act
  on it, even if they had already started work.
- A `REOPENED` ticket stays with its agent, who can start work on it again
  directly.

Response `200 OK`: the updated ticket.

---

#### Close Ticket

```
POST /api/tickets/{id}/close
```

No request body. The closing user is the authenticated user.

Closes a resolved ticket. `status` becomes `CLOSED`, and `closedAt` and
`updatedAt` are set. `resolvedAt` is kept.

Two users may close:
- **The ticket's customer**, at any time after resolution, to confirm the
  issue is fixed.
- **An `ORG_ADMIN` of the ticket's organization**, on the customer's behalf,
  but only once the customer has had **3 hours** after `resolvedAt` to respond.
  An earlier attempt returns `409` with the time closing becomes available.

Rules, checked in this order:

| # | Rule | Failure |
|---|---|---|
| 1 | Ticket exists and is the customer's own, or belongs to the admin's organization | `404 Not Found` |
| 2 | The authenticated user is the ticket's customer or an `ORG_ADMIN` of its organization | `403 Forbidden` |
| 3 | Ticket is `RESOLVED` | `409 Conflict` |
| 4 | If an admin: at least 3 hours have passed since `resolvedAt` | `409 Conflict` |

The assigned agent cannot close a ticket they resolved.

Early admin close:
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "The customer has 3 hours after resolution to close this ticket; an administrator can close it from 2026-09-14T15:00",
  "path": "/api/tickets/1/close"
}
```

---

#### Reopen Ticket

```
POST /api/tickets/{id}/reopen
```

No request body. The customer is the authenticated user.

The customer reports that the issue is not actually fixed. `status` becomes
`REOPENED`, `reopenCount` goes up by one and `updatedAt` is refreshed.
`resolvedAt` and `closedAt` are cleared, because the ticket is now neither
resolved nor closed; they are set again when that next happens.

The ticket **keeps its assigned agent**, who can start work on it again
straight away. An admin can still reassign it to a different agent.

Rules, checked in this order:

| # | Rule | Failure |
|---|---|---|
| 1 | Ticket exists and is the customer's own | `404 Not Found` |
| 2 | The authenticated user is the ticket's customer | `403 Forbidden` |
| 3 | Ticket is `RESOLVED` or `CLOSED` | `409 Conflict` |
| 4 | If `CLOSED`: no more than 7 days have passed since `closedAt` | `409 Conflict` |

After the reopen window the customer is asked to open a new ticket instead.

Response `200 OK`: the updated ticket.

---

#### Delete Ticket

```
DELETE /api/tickets/{id}
```

`ORG_ADMIN` of the ticket's organization only.

Response `204 No Content`: no body. The ticket's messages are deleted with it.

Response `404 Not Found`: standard error body.

---

### Messages API

Every ticket has its own conversation between the customer who opened it and
the agent currently assigned to it.

| | Post | Read |
|---|---|---|
| The ticket's customer | ✅ | ✅ |
| The ticket's current assigned agent | ✅ | ✅ |
| `ORG_ADMIN` of the ticket's organization | ❌ | ✅ read-only oversight |
| Anyone else, including other customers, unassigned or previously assigned agents, and other organizations' admins | ❌ | ❌ |

Inactive users can do neither. Access follows assignment: when a ticket is
reassigned, the previous agent loses access to its conversation and the new
agent can read the whole history.

#### Post Message

```
POST /api/tickets/{ticketId}/messages
Content-Type: application/json
```

Request body:
```json
{
  "content": "The printer shows error 50.4 after the paper jam."
}
```

The sender is the authenticated user.

Response `201 Created`:
```json
{
  "id": 12,
  "ticketId": 1,
  "sender": {
    "id": 1,
    "name": "Dana Customer",
    "email": "dana@acme.com",
    "role": "CUSTOMER"
  },
  "content": "The printer shows error 50.4 after the paper jam.",
  "createdAt": "2026-09-14T12:00:00"
}
```

- Leading and trailing whitespace is trimmed.
- The customer can post on an `OPEN` ticket before any agent is assigned, to
  add detail.
- Posting on a `CLOSED` ticket is refused with `409`
  (`Cannot post a message on a CLOSED ticket; reopen it first`). The customer
  reopens the ticket to continue the conversation.

| Failure | Status |
|---|---|
| Blank content, or content over 5000 characters | `400 Bad Request` |
| Unknown ticket, or a ticket outside the sender's scope (another customer's, or not assigned to this agent) | `404 Not Found` |
| An `ORG_ADMIN` or `SUPER_ADMIN` tries to post | `403 Forbidden` |
| Ticket is `CLOSED` | `409 Conflict` |

#### Get Messages

```
GET /api/tickets/{ticketId}/messages
```

Returns the conversation oldest first. A `CLOSED` ticket's conversation stays
readable. Senders are loaded in the same query as the messages, so the cost of
reading a thread does not grow with the number of participants.

`sender` is `null` for a message whose author's account has been deleted.

| Failure | Status |
|---|---|
| Unknown ticket, or a ticket outside the reader's scope | `404 Not Found` |
| A `SUPER_ADMIN` tries to read | `403 Forbidden` |

---

## DTOs

No JPA entity is ever bound to a request body or returned from a controller.
Requests are bound to request records, responses are built from entities by
hand-written mappers (`OrganizationMapper`, `UserMapper`, `TicketMapper`), so
adding a field to an entity can never silently widen an API response.

### Request DTOs

#### LoginRequest

| Field | Type | Constraints |
|---|---|---|
| `email` | String | required |
| `password` | String | required |

#### CreateOrganizationRequest

| Field | Type | Constraints |
|---|---|---|
| `name` | String | required, max 150 |
| `companyEmail` | String | required, valid email, max 200 |
| `domain` | String | required, max 150 |
| `industry` | String | required, max 100 |

#### CreateUserRequest

| Field | Type | Constraints |
|---|---|---|
| `name` | String | required, max 150 |
| `email` | String | required, valid email, max 200 |
| `password` | String | required, 8 to 100 characters |
| `phoneNumber` | String | optional, 7 to 20 characters |
| `role` | UserRole | required |
| `organizationId` | Long | `SUPER_ADMIN` creator: required except for a new `SUPER_ADMIN`. `ORG_ADMIN` creator: optional, must be their own |

#### CreateTicketRequest

| Field | Type | Constraints |
|---|---|---|
| `title` | String | required, max 200 |
| `description` | String | required, max 5000 |
| `category` | TicketCategory | required |

#### AssignTicketRequest

| Field | Type | Constraints |
|---|---|---|
| `agentId` | Long | required |

#### PostMessageRequest

| Field | Type | Constraints |
|---|---|---|
| `content` | String | required, not blank, max 5000 |

#### UpdateTicketRequest

| Field | Type | Constraints |
|---|---|---|
| `title` | String | required, max 200 |
| `description` | String | required, max 5000 |
| `category` | TicketCategory | required |

### Response DTOs

| DTO | Contents |
|---|---|
| `OrganizationResponse` | `id`, `name`, `companyEmail`, `domain`, `industry` |
| `OrganizationSummaryResponse` | `id`, `name` — used when nested in another response |
| `UserResponse` | `id`, `name`, `email`, `phoneNumber`, `role`, `active`, `organization` |
| `UserSummaryResponse` | `id`, `name`, `email`, `role` — used when nested in another response |
| `TicketResponse` | all ticket fields, with `customer`, `assignedAgent` and `organization` as summaries |
| `PageResponse<T>` | `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last` |
| `LoginResponse` | `accessToken`, `tokenType` (`Bearer`), `expiresAt`, `user` (`UserResponse`) |
| `MessageResponse` | `id`, `ticketId`, `sender` (summary, or `null`), `content`, `createdAt` |

`password` is not a component of any response record, so it cannot be
serialised even by accident.

---

## Database Constraints and Indexes

The rules that matter most are enforced by the database itself, not only by
application code, and are tested with plain SQL against both H2 and
PostgreSQL.

| Rule | Enforced by |
|---|---|
| Deleting an agent keeps their tickets, now unassigned | `tickets.assigned_agent_id` foreign key `ON DELETE SET NULL` |
| Deleting a user keeps their messages, without a sender | `messages.sender_id` foreign key `ON DELETE SET NULL` |
| Deleting a ticket deletes its messages | `messages.ticket_id` foreign key `ON DELETE CASCADE` |
| A customer with tickets, or an organization with users or tickets, cannot be deleted | plain foreign keys (restrict) |
| Ticket numbers are unique | `uk_tickets_ticket_number` |
| Emails are unique | unique constraint on `users.email` |
| Required fields are present | `NOT NULL` on ticket title, description, status, priority, category, customer, organization, reopen count and timestamps; user name, email, password, role and active flag; all organization fields |
| Enum columns hold only known values | check constraints generated for status, priority, category and role |

Indexes back each role's scoped list:

| Index | Serves |
|---|---|
| `idx_tickets_organization_created_at` | an admin's organization ticket list, newest first |
| `idx_tickets_customer_created_at` | a customer's ticket list |
| `idx_tickets_assigned_agent_created_at` | an agent's ticket list |
| `idx_tickets_status` | status filters |
| `idx_users_organization_role` | organization user lists, e.g. agents to assign |
| `idx_messages_ticket_created_at` | reading a ticket's conversation in order |

The schema is generated by Hibernate (`ddl-auto=update`). On a **new** database
every constraint and index above is created. On a database created **before**
these were added, `update` adds missing tables and columns but is not relied on
to add indexes, and it does not add `NOT NULL`, unique or `ON DELETE` rules to
existing columns and foreign keys. For a development or demo database the simplest fix is to recreate the
schema. For a database with data worth keeping, apply the changes with a
migration tool such as Flyway before relying on them.

---

## Exception Handling

All exceptions are handled globally by `GlobalExceptionHandler`
(`@RestControllerAdvice`). Every failure returns the same `ApiErrorResponse`
shape, so a client only ever has to parse one structure:

```json
{
  "timestamp": "2026-06-18T15:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Ticket with ID 99 not found",
  "path": "/api/tickets/99"
}
```

| Exception | HTTP Status | Message |
|---|---|---|
| `OrganizationNotFoundException` | `404 Not Found` | `Organization with id X not found` |
| `UserNotFoundException` | `404 Not Found` | `User with ID X not found` |
| `TicketNotFoundException` | `404 Not Found` | `Ticket with ID X not found` |
| `BusinessRuleException` | `400 Bad Request` | the rule that was violated |
| `InvalidCredentialsException` | `401 Unauthorized` | `Invalid email or password` |
| Spring Security `AuthenticationException` | `401 Unauthorized` | `Authentication is required`, or `Invalid or expired access token` |
| Spring Security `AccessDeniedException` | `403 Forbidden` | `You do not have permission to perform this action` |
| `EmailAlreadyInUseException` | `409 Conflict` | `An account with this email already exists` |
| `DataIntegrityViolationException` | `409 Conflict` | `The request conflicts with existing data` |
| `ForbiddenOperationException` | `403 Forbidden` | why the acting user may not do this |
| `InvalidTicketStateException` | `409 Conflict` | e.g. `Cannot resolve a ticket with status ASSIGNED`, or a time window that has not opened or has expired |
| `MethodArgumentNotValidException` | `400 Bad Request` | `Validation failed`, plus `fieldErrors` |
| `HttpMessageNotReadableException` | `400 Bad Request` | `Malformed or unreadable request body` |
| `MethodArgumentTypeMismatchException` | `400 Bad Request` | `Invalid value for parameter 'x'` |
| `NoResourceFoundException` (unknown URL) | `404 Not Found` | `No endpoint GET /api/...` |
| other Spring MVC errors, e.g. unsupported method, unsupported content type, missing query parameter | their own status, e.g. `405`, `415`, `400` | Spring's short description, e.g. `Method 'PATCH' is not supported.` |
| any other `Exception` | `500 Internal Server Error` | `An unexpected error occurred` |

Genuine `500`s are logged in full on the server, with method and path, but
the client only ever sees the generic message.

### Validation errors

Bean Validation failures report every rejected field at once, in an extra
`fieldErrors` object. That key is absent from all other error responses.

```json
{
  "timestamp": "2026-06-18T15:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/tickets",
  "fieldErrors": {
    "title": "Title is required",
    "category": "Category is required"
  }
}
```

An unknown enum constant, such as a category of `BANANA`, is reported as a
`400` rather than surfacing as a `500`. The underlying Jackson message is not
echoed back because it exposes internal type names.

---

## Testing

By default the suite runs against in-memory H2 in PostgreSQL compatibility
mode, so it needs neither a live database nor any environment variables:

```bash
./mvnw test
```

The same suite runs against a real **PostgreSQL 17** container, started
automatically by Testcontainers, when Docker is running:

```bash
SPRING_PROFILES_ACTIVE=postgres ./mvnw test
```

Both runs are expected to pass. The PostgreSQL run is the one that proves
database-specific behaviour: constraints, `ON DELETE` rules, index creation,
`LIKE` escaping and sort order.

| Test | Kind | Covers |
|---|---|---|
| `TicketServiceTest` | unit (Mockito) | organization derived from the customer, server-controlled fields on create, ticket number generation, update touching only title/description/category |
| `UserServiceTest` | unit (Mockito) | BCrypt hashing, email normalisation and uniqueness; what a `SUPER_ADMIN` and an `ORG_ADMIN` may each create, including refusal of admin roles and other organizations for `ORG_ADMIN`; non-admins refused; who may view a user |
| `TicketWorkflowServiceTest` | unit (Mockito) | every assignment rule: valid assignment and reassignment, idempotent same-agent assign, each non-admin role, inactive and cross-organization admin, each non-agent role, inactive and cross-organization agent, each non-assignable status, and that nothing is saved on any rejection; start and resolve by the assigned agent, refusal of every other actor (other agent, admin, customer, unassigned ticket, deactivated agent, changed role), every invalid source status, and `resolvedAt` handling; reassigning `IN_PROGRESS` tickets back to `ASSIGNED` without undoing same-agent progress; reassigning and restarting `REOPENED` tickets; reopen and close by every allowed and refused actor, every invalid status, reopen-window and admin-close-window boundaries against a fixed clock |
| `TicketWorkflowPropertiesTest` | unit (Spring `Binder`) | `helpdesk.tickets.*` defaults, overrides from environment variables named as documented, rejection of negative windows |
| `FrameworkErrorMappingTest` | web slice (`@WebMvcTest`) | unknown URL, unsupported method and unsupported content type keep their real `404`/`405`/`415` status in the standard error shape, without leaking class names |
| `MessageServiceTest` | unit (Mockito) | posting by customer and assigned agent, trimming, posting on an unassigned ticket, posting allowed in every status except `CLOSED`; admin, unassigned agent, other customer and inactive users refused; reading by customer, agent and admin, refusal of unrelated and cross-organization users, closed threads readable, deleted senders mapped to `null` |
| `MessageControllerTest` | web slice (`@WebMvcTest`) | `201` and `200` responses, validation of content, sender taken from the token rather than the body, role rules, `403` and `409` mapping |
| `RuleBasedTicketPriorityPolicyTest` | unit | every category baseline; every incident, urgency, low-urgency and calm phrase in the lists; precedence between them; whole-word, case-insensitive and typographic-apostrophe matching; the documented negation limitation; reopen escalation and its `HIGH` ceiling; determinism and null safety |
| `UserControllerTest` | web slice (`@WebMvcTest` with the real `SecurityConfig`) | create, list and get users: role rules, validation of every field, `409` for duplicate emails, binding of filters and paging, sort whitelist, `401` without a token |
| `OrganizationControllerTest` | web slice (`@WebMvcTest` with the real `SecurityConfig`) | super-admin-only create and list, search and paging binding, `404` mapping, body validation |
| `TicketControllerTest` | web slice (`@WebMvcTest` with the real `SecurityConfig`) | `401` without or with a malformed token; every role refused by every endpoint's `@PreAuthorize`; the authenticated user's id passed to the services; status codes, per-field validation messages, unknown enum handled as `400`, error shape, absence of password and nested entity internals, assign, start, resolve, close and reopen mapped to `200`/`400`/`403`/`409` |
| `AuthIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | login success and the token's minimal claims; case-insensitive email; identical `401` for wrong password, unknown email and inactive account; hashed passwords at rest; rejection of missing, expired, forged-signature, wrong-issuer and unsigned tokens; deactivation and role changes applying to existing tokens immediately; bootstrap super admin login; public API docs |
| `AccessControlIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | creating users as each role, within and across organizations, duplicate emails; organization and user visibility; reading, editing, deleting and listing tickets as each kind of user, with out-of-scope reads indistinguishable from missing ids |
| `TenantIsolationIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | exact ticket lists per role across two organizations, lists following reassignment, a query-count check that listing does not run a query per ticket; every cross-organization ticket, message, workflow, user and organization request answered `404`; scoped user lists; organization, customer, agent and status fields ignored on create and edit; no endpoint to modify a user; super admin read-only on tickets |
| `SecurityStartupTasksIntegrationTest` | end-to-end (`@SpringBootTest`) | plain-text passwords hashed on startup with login still working and a second run changing nothing; bootstrap super admin never overwritten |
| `JwtPropertiesTest` | unit | refusal to start without a signing secret, with one under 32 bytes, or with a non-positive token lifetime |
| `TicketApiIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | full organization to user to ticket flow through the real web, service and persistence layers, asserting no `password` or `hibernateLazyInitializer` anywhere in the payload |
| `TicketAssignmentIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | assignment persisted and readable back, reassignment, and that cross-organization agents, cross-organization admins, non-admin actors, non-agent targets and unknown ids are rejected with the stored ticket left `OPEN` and unassigned |
| `TicketAgentWorkflowIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | full `OPEN` → `ASSIGNED` → `IN_PROGRESS` → `RESOLVED` lifecycle persisted; resolve-before-start, double start and double resolve refused; other agents and the admin forbidden; reassignment transferring ownership, including of in-progress work; no reassignment once resolved |
| `TicketMessagingIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | a customer–agent conversation read back in order by all three allowed readers; admin read-only; outsiders refused; access moving with reassignment; closed ticket frozen until reopened; trimming and validation; deleting a ticket deleting its messages; and a query-count check that reading a thread does not run a query per sender (Hibernate statistics) |
| `TicketPriorityIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | priority set on create for each level; a client-supplied `priority` ignored; recalculation on edit in both directions; escalation over three reopens capped at `HIGH`; startup backfill filling only missing priorities |
| `TicketSearchIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | walking every page without repeats or gaps; priority sorted by severity and status by lifecycle; combined status, category, agent and unassigned filters; search over number, title and description; `%` and `_` matched literally; filters unable to widen scope; user and organization search |
| `DatabaseConstraintsIntegrationTest` | end-to-end (`@SpringBootTest` + JDBC) | with plain SQL: deleting an agent unassigns their tickets and orphans their messages; customers and organizations in use cannot be deleted; unique ticket numbers and emails; `NOT NULL` on every required column; enum check constraints; presence of every index |
| `CorsAndTimeIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | preflight and actual requests allowed for the configured origin without credentials, other origins refused; the application and its timestamps in UTC |
| `OpenApiIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | Swagger UI served; bearer JWT scheme required by default; login public; `ApiErrorResponse` documented on error responses; no `currentUserId` parameter leaked; list parameters documented; every operation tagged and summarised |
| `TicketReopenCloseIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | close, reopen, re-resolve and close again with `reopenCount` persisted; admin close refused before 3 hours and allowed after; reopen refused after 7 days; reassignment of a reopened ticket; other customers, the agent and a cross-organization admin forbidden. Time windows are tested by advancing a `MutableClock` rather than waiting |

---

## Configuration

`application.properties`:

```properties
spring.application.name=HelpDesk
server.port=${PORT:8080}

spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false

helpdesk.tickets.admin-close-after=PT3H
helpdesk.tickets.reopen-window=P7D

helpdesk.security.jwt.secret=${JWT_SECRET:}
helpdesk.security.jwt.access-token-ttl=PT1H
helpdesk.security.jwt.issuer=helpdesk

helpdesk.bootstrap.super-admin.email=${BOOTSTRAP_SUPER_ADMIN_EMAIL:}
helpdesk.bootstrap.super-admin.password=${BOOTSTRAP_SUPER_ADMIN_PASSWORD:}
helpdesk.bootstrap.super-admin.name=${BOOTSTRAP_SUPER_ADMIN_NAME:Super Admin}
```

All sensitive values are driven by environment variables:

| Variable | Description |
|---|---|
| `PORT` | Server port (defaults to `8080`) |
| `DB_URL` | JDBC connection URL, e.g. `jdbc:postgresql://localhost:5432/helpdesk` |
| `DB_USERNAME` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |
| `JWT_SECRET` | **Required.** Random signing key of at least 32 characters. The application refuses to start without it. Generate one with `openssl rand -base64 48` |
| `BOOTSTRAP_SUPER_ADMIN_EMAIL` | Email of the first super admin, created on startup if it does not exist |
| `BOOTSTRAP_SUPER_ADMIN_PASSWORD` | That super admin's initial password, at least 12 characters; the application refuses to start with a shorter one. After the first start, sign in and change it, then remove the variable |
| `BOOTSTRAP_SUPER_ADMIN_NAME` | Optional display name, default `Super Admin` |
| `BOOTSTRAP_SUPER_ADMIN_RESET_PASSWORD` | `false` by default. `true` resets the existing super admin's password to `BOOTSTRAP_SUPER_ADMIN_PASSWORD` on the next start and reactivates the account: the way back in after a forgotten password. Unset it afterwards |
| `API_DOCS_ENABLED` | `true` (default) serves Swagger UI and the OpenAPI document; `false` hides both |
| `FRONTEND_BASE_URL` | Where the browser app is served, used to build password reset links. Default `http://localhost:5173` |
| `MAIL_FROM` | Sender address for password reset emails. Default `no-reply@helpdesk.local` |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Set these to email reset links instead of writing them to the log |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins allowed to call the API from another site, such as the hosted frontend (`https://helpdesk-web.onrender.com`). Empty by default, which allows no cross-origin calls. `*` is refused |

`ddl-auto=update` means Hibernate will automatically create or alter tables to match the entity definitions on startup.

> **Note:** `update` adds missing tables and columns but never changes the
> type of an existing column. The ticket `title` and `description` columns are
> `varchar(200)` and `varchar(5000)` to match request validation. A database
> created before that change still has `varchar(255)` for both, which rejects
> longer descriptions with a `500`. Widen them once by hand:
>
> ```sql
> ALTER TABLE tickets ALTER COLUMN description TYPE varchar(5000);
> ```
>
> `title` can stay at `varchar(255)`, which already fits the 200-character limit.

**Health checks.** Point a hosting platform's health check at
`/actuator/health/liveness`. It does not depend on the database, so a slow
database wake-up does not get the service restarted. `/actuator/health`
includes the database.

**Behind a proxy.** `server.forward-headers-strategy=native` honours
`X-Forwarded-*` headers from private-network proxies, so URLs the API
generates, such as Swagger's, use `https` on hosting platforms.

**Memory.** The container sizes the JVM heap from its memory limit. Measured
under load, the API needs a **512 MB** instance: it used about 315 MB and
served 600 requests without errors at 512 MB, and it is killed on startup at
256 MB even with tuning.

**Time zone.** The application always runs in UTC, whatever the host's zone.
Timestamps in responses, such as `createdAt`, carry no zone and are UTC;
clients convert them to the viewer's local time.

`open-in-view=false` is safe here because every entity-to-DTO mapping happens
inside a transactional service method, so no lazy association is ever touched
during view rendering.

Ticket workflow time windows use ISO-8601 durations and can be overridden per
environment, for example with `HELPDESK_TICKETS_ADMIN_CLOSE_AFTER=PT2H` or `HELPDESK_TICKETS_REOPEN_WINDOW=P14D`:

| Property | Default | Meaning |
|---|---|---|
| `helpdesk.tickets.admin-close-after` | `PT3H` | How long the customer has to close a resolved ticket before an org admin may close it instead |
| `helpdesk.tickets.reopen-window` | `P7D` | How long after closure the customer may still reopen a ticket |

---

## Frontend

The site opens on a **public landing page** explaining what HelpDesk is, how a
ticket moves, and who each role is for; signing in is one click from it, and a
signed-in visitor goes straight to their dashboard. There is no public sign-up:
accounts are created by an administrator.

A React + TypeScript + Vite app in [`frontend/`](frontend/README.md) covers every
role's screens against the real API: customer, support agent, organization
admin and super admin. It has protected, role-aware routes, a centralized API
client, loading, empty and error states, confirmation dialogs, URL-based filters
and a responsive layout. Details are in [frontend/README.md](frontend/README.md).

### Run the whole stack locally

1. Start the backend (see [Running Locally](#running-locally) or
   [Running with Docker](#running-with-docker)).
2. Start the frontend:

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

3. Open http://localhost:5173 and sign in. To try every role, load demo data
   into an empty local database with `npm run seed:demo` (see
   [frontend/README.md](frontend/README.md#demo-data)).

### Deploying the frontend on Render

Create a **Static Site** from this repository:

| Setting | Value |
|---|---|
| Root directory | `frontend` |
| Build command | `npm ci && npm run build` |
| Publish directory | `dist` |
| Environment variable | `VITE_API_BASE_URL` = the backend's URL, e.g. `https://helpdesk-ticketing-system-mi7f.onrender.com` |
| Optional, demo deployments only | `VITE_DEMO_MODE=true` and `VITE_DEMO_PASSWORD=<seeded demo password>` add one-click demo sign-in for the four roles. Anything in a `VITE_` variable is public, so never set these for a deployment with real data |
| Redirects/Rewrites | Rewrite `/*` to `/index.html`, so links such as `/tickets/42` load the app |

Then allow the site to call the API by setting `CORS_ALLOWED_ORIGINS` on the
**backend** service to the static site's URL, e.g.
`https://helpdesk-web.onrender.com`.

---

## Running Locally

### Prerequisites

- Java 25
- Maven 3.9+ (or use the included `./mvnw`)
- PostgreSQL running locally

### Steps

1. Create the database:

```sql
CREATE DATABASE helpdesk;
```

2. Set environment variables:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/helpdesk
export DB_USERNAME=postgres
export DB_PASSWORD=yourpassword
export JWT_SECRET="$(openssl rand -base64 48)"
export BOOTSTRAP_SUPER_ADMIN_EMAIL=admin@example.com
export BOOTSTRAP_SUPER_ADMIN_PASSWORD=choose-a-strong-password
```

3. Build and run:

```bash
./mvnw spring-boot:run
```

4. The API is available at `http://localhost:8080`. Log in as the bootstrap
   super admin with `POST /api/auth/login`, then create an organization and its
   users.

> **Deployed API:** `https://helpdesk-ticketing-system-mi7f.onrender.com`

---

## Running with Docker

### Dockerfile

The [`Dockerfile`](Dockerfile) builds in two stages:

1. **Build:** the full JDK compiles the jar with Maven. Dependencies are
   downloaded in their own layer, so they are cached until `pom.xml` changes.
2. **Run:** only a Java 25 **JRE** and the jar, running as an unprivileged
   `helpdesk` user rather than root. The JVM sizes its heap from the
   container's memory limit and exits on out-of-memory so the platform
   restarts it cleanly.

The image is about 620 MB. `.dockerignore` keeps `.env` files, the frontend,
build output and Git data out of the build context, so local secrets never end
up in an image layer.

### Build the image

```bash
docker build -t helpdesk-ticketing-system .
```

### Run the container

```bash
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/helpdesk \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=yourpassword \
  -e JWT_SECRET="$(openssl rand -base64 48)" \
  -e BOOTSTRAP_SUPER_ADMIN_EMAIL=admin@example.com \
  -e BOOTSTRAP_SUPER_ADMIN_PASSWORD=choose-a-strong-password \
  helpdesk-ticketing-system
```

> Use `host.docker.internal` to connect to PostgreSQL running on your local machine from inside the container.

### Using Docker Compose (recommended)

[`compose.yaml`](compose.yaml) runs PostgreSQL 17 and the API together, with
the database kept in a named volume and both containers restarting with Docker.

1. Create your local settings. `.env` is ignored by git, so real values never
   get committed:

   ```bash
   cp .env.example .env
   ```

   Fill in `DB_PASSWORD`, `JWT_SECRET` (`openssl rand -base64 48`) and
   `BOOTSTRAP_SUPER_ADMIN_PASSWORD`. Compose refuses to start if the database
   password or JWT secret is missing.

2. Start the stack:

   ```bash
   docker compose up -d --build
   ```

The API is on http://localhost:8080 and PostgreSQL on port `55432`, so it does
not clash with another PostgreSQL on `5432`. Both are published only on
`127.0.0.1`, so other devices on your network cannot reach them. Compose marks
the API healthy once `/actuator/health/liveness` reports `UP`.

| Task | Command |
|---|---|
| See status | `docker compose ps` |
| Follow the API's logs | `docker compose logs -f api` |
| Stop, keeping data | `docker compose down` |
| Stop and delete all data | `docker compose down -v` |

### When the stack will not start

| Symptom | Cause | Fix |
|---|---|---|
| `api` restarts, logs show `FATAL: password authentication failed for user "helpdesk"` | `DB_PASSWORD` in `.env` was changed after the database volume was created. PostgreSQL only applies `POSTGRES_PASSWORD` when it first initialises its data directory | Change the password inside the running database, which keeps the data: `docker compose exec db psql -U helpdesk -d helpdesk -c "ALTER USER helpdesk WITH PASSWORD 'the-value-from-.env';"` then `docker compose restart api`. Or start over with `docker compose down -v` |
| `api` restarts, logs show `Unable to determine Dialect` | Same as above: the API cannot reach the database at all | As above; the dialect error is a symptom, not the cause |
| Signing in as the super admin fails after changing `BOOTSTRAP_SUPER_ADMIN_PASSWORD` | That variable only applies when the account is created. An existing account keeps its password, and the log says so at startup | Sign in with the old password and change it in the app, or set `BOOTSTRAP_SUPER_ADMIN_RESET_PASSWORD=true` for one restart, then unset it |
| `docker compose up` exits with `Set DB_PASSWORD in .env` | No `.env`, or the value is empty | `cp .env.example .env` and fill it in |

---

## Postman Screenshots

> **Note:** these screenshots were taken before authentication was added.
> Requests now need an `Authorization: Bearer <token>` header, and the
> `customerId` field shown when creating a ticket no longer exists; the customer
> is the logged-in user.

The following screenshots demonstrate the API working end-to-end via Postman.

### 1. Create Organization

![Create Organization](ss/WhatsApp%20Image%202026-06-18%20at%204.04.10%20PM.jpeg)

Creates a new organization by sending a POST request to `/api/organizations` with `name`, `companyEmail`, `domain`, and `industry` in the request body. The response returns the created organization with its auto-generated `id`.

---

### 2. Create User

![Create User](ss/WhatsApp%20Image%202026-06-18%20at%204.04.44%20PM.jpeg)

Creates a new user by sending a POST request to `/api/users`. The `organizationId` in the body links the user to an existing organization. The response returns a `UserResponse`, which has no password field at all.

> These screenshots were captured before the API hardening change. Create
> endpoints now return `201 Created`, delete returns `204 No Content`, the user
> request body takes `organizationId` instead of a nested `organization` object,
> and nested users and organizations are returned as summaries.

---

### 3. Create Ticket

![Create Ticket](ss/WhatsApp%20Image%202026-06-18%20at%204.05.24%20PM.jpeg)

Creates a support ticket by sending a POST request to `/api/tickets` with `title`, `description`, `category`, and `customerId`. The service automatically resolves the customer's organization, sets the status to `OPEN`, and generates the `ticketNumber` in `HD-2026-XXXXXX` format.

---

### 4. Get Ticket by ID

![Get Ticket by ID](ss/WhatsApp%20Image%202026-06-18%20at%204.06.25%20PM.jpeg)

Fetches a specific ticket by its ID using `GET /api/tickets/{id}`. Returns the full ticket object including all relationships and timestamps. Returns `404` with a descriptive message if the ticket is not found.

---

### 5. Get All Tickets

![Get All Tickets](ss/WhatsApp%20Image%202026-06-18%20at%204.06.57%20PM.jpeg)

Fetches all tickets using `GET /api/tickets`. Returns an array of all ticket objects.

---

### 6. Update Ticket

![Update Ticket](ss/WhatsApp%20Image%202026-06-18%20at%204.07.44%20PM.jpeg)

Updates a ticket's `title`, `description`, and `category` using `PUT /api/tickets/{id}`. Automatically updates `updatedAt` to the current timestamp.

---

## Author

Ibrahim Poonawala
