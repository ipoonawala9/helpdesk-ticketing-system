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
- [API Reference](#api-reference)
  - [Organizations](#organizations-api)
  - [Users](#users-api)
  - [Tickets](#tickets-api)
  - [Messages](#messages-api)
- [DTOs](#dtos)
- [Exception Handling](#exception-handling)
- [Testing](#testing)
- [Configuration](#configuration)
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
| Boilerplate reduction | Lombok |
| API Docs | Postman |
| Build tool | Maven (Maven Wrapper included) |
| Containerization | Docker (eclipse-temurin:25-jdk) |
| Testing | JUnit 5, Mockito, AssertJ, MockMvc, H2 (in-memory) |

---

## Project Structure

```
helpdesk-ticketing-system/
├── src/
│   ├── main/
│   │   ├── java/com/ibrahim/helpdesk/
│   │   │   ├── HelpDeskApplication.java          # Entry point
│   │   │   ├── exception/
│   │   │   │   ├── ApiErrorResponse.java         # single error shape
│   │   │   │   ├── BusinessRuleException.java
│   │   │   │   ├── ForbiddenOperationException.java
│   │   │   │   ├── GlobalExceptionHandler.java   # @RestControllerAdvice
│   │   │   │   ├── InvalidTicketStateException.java
│   │   │   │   ├── OrganizationNotFoundException.java
│   │   │   │   ├── TicketNotFoundException.java
│   │   │   │   └── UserNotFoundException.java
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
│   │   │   │   └── service/UserService.java
│   │   │   └── ticket/
│   │   │       ├── config/TicketWorkflowConfig.java      # Clock bean
│   │   │       ├── config/TicketWorkflowProperties.java  # helpdesk.tickets.*
│   │   │       ├── controller/TicketController.java
│   │   │       ├── dto/AgentActionRequest.java
│   │   │       ├── dto/AssignTicketRequest.java
│   │   │       ├── dto/CloseTicketRequest.java
│   │   │       ├── dto/CreateTicketRequest.java
│   │   │       ├── dto/ReopenTicketRequest.java
│   │   │       ├── dto/TicketResponse.java
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
│   │   │       ├── service/TicketParticipants.java     # who is customer / agent / admin of a ticket
│   │   │       ├── service/TicketService.java          # CRUD
│   │   │       └── service/TicketWorkflowService.java  # status transitions
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       ├── java/com/ibrahim/helpdesk/
│       │   ├── ApiIntegrationTestSupport.java     # shared end-to-end helpers
│       │   ├── MutableClock.java                  # test clock that can be advanced
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
│       │   ├── ticket/config/TicketWorkflowPropertiesTest.java
│       │   ├── ticket/controller/TicketControllerTest.java
│       │   ├── ticket/priority/RuleBasedTicketPriorityPolicyTest.java
│       │   ├── ticket/service/TicketServiceTest.java
│       │   ├── ticket/service/TicketWorkflowServiceTest.java
│       │   └── user/service/UserServiceTest.java
│       └── resources/
│           └── application.properties             # in-memory H2
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
| `password` | String | `@JsonIgnore` | Password (never returned in API responses) |
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
| `assignedAgent` | User | `@ManyToOne` — the support agent handling it |
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

## API Reference

Base URL: `https://helpdesk-ticketing-system-mi7f.onrender.com`

API tested via Postman.

---

### Organizations API

#### Create Organization

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

```
GET /api/organizations
```

Response `200 OK`: array of organization objects.

---

#### Get Organization by ID

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

- The request is bound to `CreateUserRequest`, not to the `User` entity, so `id`
  and `active` cannot be set by the caller. New users are always created active.
- `organizationId` is resolved from the database. If not found, returns `404`.
- `organizationId` is required for every role except `SUPER_ADMIN`.
- `password` is stored but is not a field on any response type, so it can never
  be returned.

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

#### Get User by ID

```
GET /api/users/{id}
```

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
  "category": "ACCOUNT",
  "customerId": 1
}
```

Behavior on creation:
- Customer is looked up by `customerId`
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
  "priority": null,
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

Response `404 Not Found` (if customer not found):
```json
{
  "timestamp": "2026-06-18T15:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "User with ID 99 not found",
  "path": "/api/tickets"
}
```

---

#### Get All Tickets

```
GET /api/tickets
```

Response `200 OK`: Array of all ticket objects.

---

#### Get Ticket by ID

```
GET /api/tickets/{id}
```

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
  "agentId": 3,
  "adminId": 2
}
```

An organization administrator assigns the ticket to a support agent. On
success the ticket's `assignedAgent` is set, `status` becomes `ASSIGNED` and
`updatedAt` is refreshed.

> **Interim identity:** `adminId` identifies the acting administrator only
> because authentication does not exist yet. Every rule below is enforced
> against that user, but the caller's claim to *be* that user is not verified.
> Once authentication is added the acting user will come from the security
> context and `adminId` will be removed from the request.

Rules, checked in this order:

| # | Rule | Failure |
|---|---|---|
| 1 | Ticket exists | `404 Not Found` |
| 2 | Admin exists | `404 Not Found` |
| 3 | Admin has role `ORG_ADMIN`, is active, and belongs to the ticket's organization | `403 Forbidden` |
| 4 | Ticket status is `OPEN`, `ASSIGNED`, `IN_PROGRESS` or `REOPENED` | `409 Conflict` |
| 5 | Agent exists | `404 Not Found` |
| 6 | Agent has role `SUPPORT_AGENT`, is active, and belongs to the ticket's organization | `400 Bad Request` |

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
Content-Type: application/json
```

Request body:
```json
{
  "agentId": 3
}
```

The assigned agent begins working on the ticket. `status` moves from
`ASSIGNED` to `IN_PROGRESS` and `updatedAt` is refreshed.

#### Resolve Ticket

```
POST /api/tickets/{id}/resolve
Content-Type: application/json
```

Request body:
```json
{
  "agentId": 3
}
```

The assigned agent marks the issue as fixed. `status` moves from `IN_PROGRESS`
to `RESOLVED`, and `resolvedAt` and `updatedAt` are set to the same timestamp.
The ticket is **not** closed; `closedAt` stays `null` until closure.

> **Interim identity:** as with `adminId`, `agentId` identifies the acting
> agent only until authentication exists. The rules are enforced against that
> user, but the claim to be that user is not verified.

Rules for both actions, checked in this order:

| # | Rule | Failure |
|---|---|---|
| 1 | Ticket exists | `404 Not Found` |
| 2 | Acting user exists | `404 Not Found` |
| 3 | Acting user is the ticket's current `assignedAgent`, still has role `SUPPORT_AGENT`, and is active | `403 Forbidden` |
| 4 | Ticket is `ASSIGNED` or `REOPENED` (start), or `IN_PROGRESS` (resolve) | `409 Conflict` |

Notes:
- Role and active flag are re-checked on every action, because either may
  have changed since the ticket was assigned.
- An unassigned ticket fails rule 3, so nobody can start it.
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
Content-Type: application/json
```

Request body:
```json
{
  "userId": 1
}
```

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
| 1 | Ticket exists | `404 Not Found` |
| 2 | Acting user exists | `404 Not Found` |
| 3 | Acting user is the ticket's customer, or an `ORG_ADMIN` of its organization | `403 Forbidden` |
| 4 | Acting user is active | `403 Forbidden` |
| 5 | Ticket is `RESOLVED` | `409 Conflict` |
| 6 | If an admin: at least 3 hours have passed since `resolvedAt` | `409 Conflict` |

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
Content-Type: application/json
```

Request body:
```json
{
  "customerId": 1
}
```

The customer reports that the issue is not actually fixed. `status` becomes
`REOPENED`, `reopenCount` goes up by one and `updatedAt` is refreshed.
`resolvedAt` and `closedAt` are cleared, because the ticket is now neither
resolved nor closed; they are set again when that next happens.

The ticket **keeps its assigned agent**, who can start work on it again
straight away. An admin can still reassign it to a different agent.

Rules, checked in this order:

| # | Rule | Failure |
|---|---|---|
| 1 | Ticket exists | `404 Not Found` |
| 2 | Acting user exists | `404 Not Found` |
| 3 | Acting user is the ticket's customer | `403 Forbidden` |
| 4 | Acting user is active | `403 Forbidden` |
| 5 | Ticket is `RESOLVED` or `CLOSED` | `409 Conflict` |
| 6 | If `CLOSED`: no more than 7 days have passed since `closedAt` | `409 Conflict` |

After the reopen window the customer is asked to open a new ticket instead.

> **Interim identity:** `userId` and `customerId` identify the acting user
> only until authentication exists, like `adminId` and `agentId`.

Response `200 OK`: the updated ticket.

---

#### Delete Ticket

```
DELETE /api/tickets/{id}
```

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
  "senderId": 1,
  "content": "The printer shows error 50.4 after the paper jam."
}
```

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
| Missing `senderId`, blank content, or content over 5000 characters | `400 Bad Request` |
| Unknown ticket or sender | `404 Not Found` |
| Sender is not the ticket's customer or current assigned agent, or is inactive | `403 Forbidden` |
| Ticket is `CLOSED` | `409 Conflict` |

#### Get Messages

```
GET /api/tickets/{ticketId}/messages?userId=1
```

Returns the conversation oldest first. A `CLOSED` ticket's conversation stays
readable. Senders are loaded in the same query as the messages, so the cost of
reading a thread does not grow with the number of participants.

`sender` is `null` for a message whose author's account has been deleted.

| Failure | Status |
|---|---|
| Missing `userId` | `400 Bad Request` |
| Unknown ticket or user | `404 Not Found` |
| User may not read this ticket's messages, or is inactive | `403 Forbidden` |

> **Interim identity:** `senderId` and `userId` identify the acting user only
> until authentication exists. The rules are enforced against that user, but
> the claim to be that user is not verified.

---

## DTOs

No JPA entity is ever bound to a request body or returned from a controller.
Requests are bound to request records, responses are built from entities by
hand-written mappers (`OrganizationMapper`, `UserMapper`, `TicketMapper`), so
adding a field to an entity can never silently widen an API response.

### Request DTOs

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
| `organizationId` | Long | required for every role except `SUPER_ADMIN` |

#### CreateTicketRequest

| Field | Type | Constraints |
|---|---|---|
| `title` | String | required, max 200 |
| `description` | String | required, max 5000 |
| `category` | TicketCategory | required |
| `customerId` | Long | required |

#### AssignTicketRequest

| Field | Type | Constraints |
|---|---|---|
| `agentId` | Long | required |
| `adminId` | Long | required; interim until authentication |

#### CloseTicketRequest

| Field | Type | Constraints |
|---|---|---|
| `userId` | Long | required; the ticket's customer or an org admin; interim until authentication |

#### ReopenTicketRequest

| Field | Type | Constraints |
|---|---|---|
| `customerId` | Long | required; interim until authentication |

#### PostMessageRequest

| Field | Type | Constraints |
|---|---|---|
| `senderId` | Long | required; interim until authentication |
| `content` | String | required, not blank, max 5000 |

#### AgentActionRequest

Used by `start` and `resolve`.

| Field | Type | Constraints |
|---|---|---|
| `agentId` | Long | required; interim until authentication |

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
| `MessageResponse` | `id`, `ticketId`, `sender` (summary, or `null`), `content`, `createdAt` |

`password` is not a component of any response record, so it cannot be
serialised even by accident.

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

The suite runs against in-memory H2 in PostgreSQL compatibility mode, so it
needs neither a live database nor any environment variables:

```bash
./mvnw test
```

| Test | Kind | Covers |
|---|---|---|
| `TicketServiceTest` | unit (Mockito) | organization derived from the customer, server-controlled fields on create, ticket number generation, update touching only title/description/category |
| `UserServiceTest` | unit (Mockito) | organization resolution, `SUPER_ADMIN` without an organization, rejection of an organization-scoped role with no organization, no password on the response record |
| `TicketWorkflowServiceTest` | unit (Mockito) | every assignment rule: valid assignment and reassignment, idempotent same-agent assign, each non-admin role, inactive and cross-organization admin, each non-agent role, inactive and cross-organization agent, each non-assignable status, and that nothing is saved on any rejection; start and resolve by the assigned agent, refusal of every other actor (other agent, admin, customer, unassigned ticket, deactivated agent, changed role), every invalid source status, and `resolvedAt` handling; reassigning `IN_PROGRESS` tickets back to `ASSIGNED` without undoing same-agent progress; reassigning and restarting `REOPENED` tickets; reopen and close by every allowed and refused actor, every invalid status, reopen-window and admin-close-window boundaries against a fixed clock |
| `TicketWorkflowPropertiesTest` | unit (Spring `Binder`) | `helpdesk.tickets.*` defaults, overrides from environment variables named as documented, rejection of negative windows |
| `FrameworkErrorMappingTest` | web slice (`@WebMvcTest`) | unknown URL, unsupported method and unsupported content type keep their real `404`/`405`/`415` status in the standard error shape, without leaking class names |
| `MessageServiceTest` | unit (Mockito) | posting by customer and assigned agent, trimming, posting on an unassigned ticket, posting allowed in every status except `CLOSED`; admin, unassigned agent, other customer and inactive users refused; reading by customer, agent and admin, refusal of unrelated and cross-organization users, closed threads readable, deleted senders mapped to `null` |
| `MessageControllerTest` | web slice (`@WebMvcTest`) | `201` and `200` responses, validation of sender and content, missing `userId` as `400`, `403` and `409` mapping |
| `RuleBasedTicketPriorityPolicyTest` | unit | every category baseline; every incident, urgency, low-urgency and calm phrase in the lists; precedence between them; whole-word, case-insensitive and typographic-apostrophe matching; the documented negation limitation; reopen escalation and its `HIGH` ceiling; determinism and null safety |
| `TicketControllerTest` | web slice (`@WebMvcTest`) | status codes, per-field validation messages, unknown enum handled as `400`, error shape, absence of password and nested entity internals, assign, start, resolve, close and reopen mapped to `200`/`400`/`403`/`409` |
| `TicketApiIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | full organization to user to ticket flow through the real web, service and persistence layers, asserting no `password` or `hibernateLazyInitializer` anywhere in the payload |
| `TicketAssignmentIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | assignment persisted and readable back, reassignment, and that cross-organization agents, cross-organization admins, non-admin actors, non-agent targets and unknown ids are rejected with the stored ticket left `OPEN` and unassigned |
| `TicketAgentWorkflowIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | full `OPEN` → `ASSIGNED` → `IN_PROGRESS` → `RESOLVED` lifecycle persisted; resolve-before-start, double start and double resolve refused; other agents and the admin forbidden; reassignment transferring ownership, including of in-progress work; no reassignment once resolved |
| `TicketMessagingIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | a customer–agent conversation read back in order by all three allowed readers; admin read-only; outsiders refused; access moving with reassignment; closed ticket frozen until reopened; trimming and validation; deleting a ticket deleting its messages; and a query-count check that reading a thread does not run a query per sender (Hibernate statistics) |
| `TicketPriorityIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | priority set on create for each level; a client-supplied `priority` ignored; recalculation on edit in both directions; escalation over three reopens capped at `HIGH`; startup backfill filling only missing priorities |
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
```

All sensitive values are driven by environment variables:

| Variable | Description |
|---|---|
| `PORT` | Server port (defaults to `8080`) |
| `DB_URL` | JDBC connection URL, e.g. `jdbc:postgresql://localhost:5432/helpdesk` |
| `DB_USERNAME` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |

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
```

3. Build and run:

```bash
./mvnw spring-boot:run
```

4. The API is available at `http://localhost:8080`

> **Deployed API:** `https://helpdesk-ticketing-system-mi7f.onrender.com`

---

## Running with Docker

### Dockerfile

```dockerfile
FROM eclipse-temurin:25-jdk

WORKDIR /app

COPY . .

RUN chmod +x mvnw

RUN ./mvnw clean package -DskipTests

EXPOSE 8080

CMD ["sh", "-c", "java -jar target/*.jar"]
```

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
  helpdesk-ticketing-system
```

> Use `host.docker.internal` to connect to PostgreSQL running on your local machine from inside the container.

### Using Docker Compose (recommended)

```yaml
version: '3.8'
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: helpdesk
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: yourpassword
    ports:
      - "5432:5432"

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:postgresql://db:5432/helpdesk
      DB_USERNAME: postgres
      DB_PASSWORD: yourpassword
    depends_on:
      - db
```

```bash
docker compose up --build
```

---

## Postman Screenshots

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
