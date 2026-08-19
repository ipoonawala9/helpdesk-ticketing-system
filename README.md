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
- [Enums](#enums)
- [API Reference](#api-reference)
  - [Organizations](#organizations-api)
  - [Users](#users-api)
  - [Tickets](#tickets-api)
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
│   │   │   │   ├── GlobalExceptionHandler.java   # @RestControllerAdvice
│   │   │   │   ├── OrganizationNotFoundException.java
│   │   │   │   ├── TicketNotFoundException.java
│   │   │   │   └── UserNotFoundException.java
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
│   │   │       ├── controller/TicketController.java
│   │   │       ├── dto/CreateTicketRequest.java
│   │   │       ├── dto/TicketResponse.java
│   │   │       ├── dto/UpdateTicketRequest.java
│   │   │       ├── entity/Ticket.java
│   │   │       ├── entity/TicketCategory.java
│   │   │       ├── entity/TicketPriority.java
│   │   │       ├── entity/TicketStatus.java
│   │   │       ├── mapper/TicketMapper.java
│   │   │       ├── repository/TicketRepository.java
│   │   │       └── service/TicketService.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       ├── java/com/ibrahim/helpdesk/
│       │   ├── HelpDeskApplicationTests.java
│       │   ├── TicketApiIntegrationTest.java      # end-to-end, H2
│       │   ├── ticket/controller/TicketControllerTest.java
│       │   ├── ticket/service/TicketServiceTest.java
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
| `priority` | TicketPriority (enum) | Urgency level |
| `category` | TicketCategory (enum) | Type of issue |
| `customer` | User | `@ManyToOne` — the user who raised the ticket |
| `assignedAgent` | User | `@ManyToOne` — the support agent handling it |
| `organization` | Organization | `@ManyToOne` — auto-inherited from customer |
| `reopenCount` | Integer | Number of times ticket was reopened |
| `createdAt` | LocalDateTime | Ticket creation timestamp |
| `updatedAt` | LocalDateTime | Last update timestamp |
| `resolvedAt` | LocalDateTime | When ticket was resolved |
| `closedAt` | LocalDateTime | When ticket was closed |

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

#### Delete Ticket

```
DELETE /api/tickets/{id}
```

Response `204 No Content`: no body.

Response `404 Not Found`: standard error body.

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
| `MethodArgumentNotValidException` | `400 Bad Request` | `Validation failed`, plus `fieldErrors` |
| `HttpMessageNotReadableException` | `400 Bad Request` | `Malformed or unreadable request body` |
| `MethodArgumentTypeMismatchException` | `400 Bad Request` | `Invalid value for parameter 'x'` |
| any other `Exception` | `500 Internal Server Error` | `An unexpected error occurred` |

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
| `TicketControllerTest` | web slice (`@WebMvcTest`) | status codes, per-field validation messages, unknown enum handled as `400`, error shape, absence of password and nested entity internals |
| `TicketApiIntegrationTest` | end-to-end (`@SpringBootTest` + MockMvc) | full organization to user to ticket flow through the real web, service and persistence layers, asserting no `password` or `hibernateLazyInitializer` anywhere in the payload |

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
```

All sensitive values are driven by environment variables:

| Variable | Description |
|---|---|
| `PORT` | Server port (defaults to `8080`) |
| `DB_URL` | JDBC connection URL, e.g. `jdbc:postgresql://localhost:5432/helpdesk` |
| `DB_USERNAME` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |

`ddl-auto=update` means Hibernate will automatically create or alter tables to match the entity definitions on startup.

`open-in-view=false` is safe here because every entity-to-DTO mapping happens
inside a transactional service method, so no lazy association is ever touched
during view rendering.

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
