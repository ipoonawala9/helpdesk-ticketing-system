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

---

## Project Structure

```
helpdesk-ticketing-system/
├── src/
│   ├── main/
│   │   ├── java/com/ibrahim/helpdesk/
│   │   │   ├── HelpDeskApplication.java          # Entry point
│   │   │   ├── exception/
│   │   │   │   ├── GlobalExceptionHandler.java   # @RestControllerAdvice
│   │   │   │   ├── OrganizationNotFoundException.java
│   │   │   │   ├── TicketNotFoundException.java
│   │   │   │   └── UserNotFoundException.java
│   │   │   ├── organization/
│   │   │   │   ├── controller/OrganizationController.java
│   │   │   │   ├── entity/Organization.java
│   │   │   │   ├── repository/OrganizationRepository.java
│   │   │   │   └── service/OrganizationService.java
│   │   │   ├── user/
│   │   │   │   ├── controller/UserController.java
│   │   │   │   ├── entity/User.java
│   │   │   │   ├── entity/UserRole.java
│   │   │   │   ├── repository/UserRepository.java
│   │   │   │   └── service/UserService.java
│   │   │   └── ticket/
│   │   │       ├── controller/TicketController.java
│   │   │       ├── dto/CreateTicketRequest.java
│   │   │       ├── dto/UpdateTicketRequest.java
│   │   │       ├── entity/Ticket.java
│   │   │       ├── entity/TicketCategory.java
│   │   │       ├── entity/TicketPriority.java
│   │   │       ├── entity/TicketStatus.java
│   │   │       ├── repository/TicketRepository.java
│   │   │       └── service/TicketService.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/ibrahim/helpdesk/
│           └── HelpDeskApplicationTests.java
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
  "message": "Organization with id 99 not found"
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
  "active": true,
  "organization": {
    "id": 1
  }
}
```

- The `organization.id` is resolved from the database. If not found, returns `404`.
- `password` is stored but never returned in any API response.

Response `200 OK`:
```json
{
  "id": 1,
  "name": "John Doe",
  "email": "john@acme.com",
  "phoneNumber": "+1234567890",
  "role": "CUSTOMER",
  "organization": {
    "id": 1,
    "name": "Acme Corp",
    "companyEmail": "contact@acme.com",
    "domain": "acme.com",
    "industry": "Technology"
  },
  "active": true
}
```

Response `404 Not Found` (if org not found):
```json
{
  "message": "Organization with id 99 not found"
}
```

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

Response `200 OK`:
```json
{
  "id": 1,
  "ticketNumber": "HD-2026-000001",
  "title": "Cannot login to dashboard",
  "description": "Getting a 403 error when trying to log in since this morning.",
  "status": "OPEN",
  "priority": null,
  "category": "ACCOUNT",
  "customer": { "id": 1, "name": "John Doe", ... },
  "assignedAgent": null,
  "organization": { "id": 1, "name": "Acme Corp", ... },
  "reopenCount": 0,
  "createdAt": "2026-06-18T15:00:00",
  "updatedAt": "2026-06-18T15:00:00",
  "resolvedAt": null,
  "closedAt": null
}
```

Response `404 Not Found` (if customer not found):
```json
{
  "message": "User with ID 99 not found"
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
  "message": "Ticket with ID 99 not found"
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

Response `200 OK`: Updated ticket object.

Response `404 Not Found`:
```json
{
  "message": "Ticket with ID 99 not found"
}
```

---

#### Delete Ticket

```
DELETE /api/tickets/{id}
```

Response `200 OK`: No body.

Response `404 Not Found`:
```json
{
  "message": "Ticket with ID 99 not found"
}
```

---

## DTOs

### CreateTicketRequest

| Field | Type | Description |
|---|---|---|
| `title` | String | Ticket title |
| `description` | String | Ticket description |
| `category` | TicketCategory | Category enum value |
| `customerId` | Long | ID of the customer raising the ticket |

### UpdateTicketRequest

| Field | Type | Description |
|---|---|---|
| `title` | String | Updated title |
| `description` | String | Updated description |
| `category` | TicketCategory | Updated category |

---

## Exception Handling

All exceptions are handled globally by `GlobalExceptionHandler` (`@RestControllerAdvice`).

| Exception | HTTP Status | Response Body |
|---|---|---|
| `OrganizationNotFoundException` | `404 Not Found` | `{"message": "Organization with id X not found"}` |
| `UserNotFoundException` | `404 Not Found` | `{"message": "User with ID X not found"}` |
| `TicketNotFoundException` | `404 Not Found` | `{"message": "Ticket with ID X not found"}` |

All three exception classes extend `RuntimeException` and pass a descriptive message to the parent constructor.

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
```

All sensitive values are driven by environment variables:

| Variable | Description |
|---|---|
| `PORT` | Server port (defaults to `8080`) |
| `DB_URL` | JDBC connection URL, e.g. `jdbc:postgresql://localhost:5432/helpdesk` |
| `DB_USERNAME` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |

`ddl-auto=update` means Hibernate will automatically create or alter tables to match the entity definitions on startup.

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

Creates a new user by sending a POST request to `/api/users`. The `organization.id` in the body links the user to an existing organization. The response returns the full user object — note that `password` is not included in the response due to `@JsonIgnore`.

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
