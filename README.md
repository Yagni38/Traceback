# TraceBack – Smart Lost-and-Found Recovery System

TraceBack is a web application that helps people get lost belongings back.
Anyone can report a **lost** or **found** item. TraceBack automatically suggests
likely matches between lost and found reports, owners can **claim** a found item,
and an **admin** verifies each claim before the item is marked as returned.

---

## Problem Statement

On college campuses, offices and public places, lost-and-found is usually a notice
board, a WhatsApp group or a cupboard at the reception desk. Reports are scattered,
nobody can search them, the person who found an item rarely meets the owner, and
items are handed over without any check that the claimant is the real owner.

TraceBack solves this by:

- keeping every lost and found report in one searchable place,
- **scoring possible matches** between lost and found reports automatically,
- letting owners submit a **claim with proof of ownership**, and
- having an **admin approve or reject** claims, so items go back to the right person,
- while **protecting personal contact details** from the public.

---

## Features

### Public website (`/`)
- Report a **LOST** or **FOUND** item (name, category, color, location, date, description, contact details).
- Dashboard counters: total, lost and found reports.
- Browse recent reports (newest first), search by name, location, category or description, and filter by type.
- **Smart matching**: shows likely matches with a match percentage and the reasons
  (same category, matching keywords, color, location, date proximity).
- **Claim a found item** with proof of ownership.
- **Duplicate claim prevention**: the same email cannot submit two pending claims for one item.
- Contact details are **masked** for the public (for example `r***@example.com`, `******3210`).
- Loading indicators, success messages, error messages and highlighting of invalid fields.

### Admin dashboard (`/admin.html`, login required)
- Statistics: total, lost, found, returned items and pending claims.
- Manage reports with full contact details and mark items as **RETURNED**.
- Review claims: **approve** or **reject**.
  - Approving a claim marks the item RETURNED and automatically rejects other pending claims on it.
  - Marking an item RETURNED directly also rejects its pending claims (after a confirmation click).
- H2 database console (`/h2-console`), admin only.

### Security and reliability
- Admin pages and admin APIs protected with **Spring Security** (HTTP Basic login).
- Admin password comes from an **environment variable**; nothing secret is stored in the code.
- Server-side **input validation** with clear, field-by-field error messages.
- Consistent JSON error responses and correct **HTTP status codes** (400, 401, 404, 409, 500).
- **Persistent database**: file-based H2 locally, **PostgreSQL in production** (configured only through environment variables).
- **Optimistic locking** (`@Version`) so two admins cannot approve conflicting claims at the same moment.
- Automated tests for the important API operations.

---

## Technologies Used

| Layer       | Technology |
|-------------|------------|
| Language    | Java 17 |
| Framework   | Spring Boot 4.1.2 (Spring Web MVC, Spring Data JPA, Bean Validation) |
| Security    | Spring Security (HTTP Basic, BCrypt password hashing) |
| Database    | H2 (file-based, local) · PostgreSQL (production) · Hibernate ORM |
| Frontend    | HTML5, CSS3, vanilla JavaScript (Fetch API) |
| Build       | Maven (Maven Wrapper included) |
| Deployment  | Docker (`Dockerfile.vercel`) on Vercel |
| Testing     | JUnit 5, Spring MockMvc, Spring Security Test |

---

## Project Architecture

```
 Browser (index.html / admin.html + JavaScript)
        │  JSON over HTTP (fetch)
        ▼
 Spring Security filter ── checks admin login for protected URLs
        ▼
 Controllers  (ItemReportController, ItemClaimController)
        │  receive request DTOs, validate with @Valid, return response DTOs
        ▼
 Services     (ReportService, ClaimService, ItemMatchingService)
        │  business rules, transactions, matching algorithm
        ▼
 Repositories (ItemReportRepository, ItemClaimRepository – Spring Data JPA)
        ▼
 H2 database file  (data/traceback_db.mv.db)
```

- **Controller layer**: HTTP only (URLs, status codes, request/response mapping).
- **Service layer**: all rules (who can claim, duplicate checks, approval steps) inside `@Transactional` methods, so an approval either fully succeeds or changes nothing.
- **DTOs**: `ReportRequest` and `ClaimRequest` define exactly what users may send, so they cannot set fields like `id` or `status`. `ReportResponse` decides what is shown and masks contact details for the public.
- **GlobalExceptionHandler**: turns every error into the same JSON shape:

```json
{
  "timestamp": "2026-11-10T23:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Enter a valid email address",
  "path": "/api/reports",
  "fieldErrors": { "reporterEmail": "Enter a valid email address" }
}
```

### Matching algorithm (score out of 100)

| Rule | Points |
|------|--------|
| Same category | 30 |
| Shared keywords in item name + description | up to 30 |
| Same color | 15 |
| Same location (or 8 for a similar location) | 15 |
| Dates within 7 days (or 5 within 30 days) | 10 |

Only **ACTIVE** reports of the **opposite type** are compared, and matches below 30% are hidden.

---

## Folder Structure

```
traceback/
├── pom.xml                         Maven build file and dependencies
├── mvnw, mvnw.cmd                  Maven Wrapper (no Maven install needed)
├── Dockerfile.vercel               Container build used by Vercel (and plain Docker)
├── .dockerignore                   Files kept out of the Docker image
├── vercel.json                     Tells Vercel to deploy the container
├── README.md
├── docs/screenshots/               Screenshots used in this README
├── data/                           H2 database files (created on first run, not in Git)
└── src/
    ├── main/
    │   ├── java/com/traceback/traceback/
    │   │   ├── TracebackApplication.java
    │   │   ├── config/             SecurityConfig, DatabaseUrlConverter
    │   │   ├── controller/         ItemReportController, ItemClaimController
    │   │   ├── dto/                ReportRequest, ClaimRequest, StatusUpdateRequest,
    │   │   │                       ReportResponse, MatchResult, ApiError
    │   │   ├── entity/             ItemReport, ItemClaim
    │   │   ├── exception/          GlobalExceptionHandler
    │   │   ├── repository/         ItemReportRepository, ItemClaimRepository
    │   │   └── service/            ReportService, ClaimService, ItemMatchingService
    │   └── resources/
    │       ├── application.properties        local settings (H2, port 8080)
    │       ├── application-prod.properties   production settings (PostgreSQL)
    │       └── static/             index.html, app.js, style.css,
    │                               admin.html, admin.js, admin.css
    └── test/
        ├── java/com/traceback/traceback/
        │   ├── ApiTestSupport.java          shared test setup
        │   ├── ReportApiTests.java
        │   ├── ClaimApiTests.java
        │   ├── PageSecurityTests.java
        │   ├── TracebackApplicationTests.java
        │   ├── config/DatabaseUrlConverterTests.java
        │   └── dto/ReportResponseMaskingTests.java
        └── resources/application-test.properties
```

---

## API Endpoints

| Method | Endpoint | Access | Description | Success |
|--------|----------|--------|-------------|---------|
| POST   | `/api/reports` | Public | Create a LOST or FOUND report | 201 |
| GET    | `/api/reports` | Public* | List all reports, newest first | 200 |
| GET    | `/api/reports/{id}` | Public* | Get one report | 200 |
| GET    | `/api/reports/{id}/matches` | Public* | Smart matches for a report | 200 |
| PATCH  | `/api/reports/{id}/status` | **Admin** | Set status `ACTIVE` or `RETURNED` | 200 |
| POST   | `/api/claims` | Public | Claim an active FOUND item | 201 |
| GET    | `/api/claims` | **Admin** | List all claims | 200 |
| PATCH  | `/api/claims/{id}/status` | **Admin** | Set claim `APPROVED` or `REJECTED` | 200 |

\* Public users receive **masked** contact details; a logged-in admin receives full details.

**Error codes:** `400` invalid input · `401` admin login missing or wrong ·
`404` report or claim not found · `409` conflict (duplicate claim, claim already reviewed,
item already returned) · `500` unexpected server error.

Example request body for `POST /api/reports`:

```json
{
  "reportType": "LOST",
  "title": "Black Wallet",
  "category": "Wallet",
  "color": "Black",
  "location": "Central Library",
  "description": "Leather wallet with college ID",
  "eventDate": "2026-11-01",
  "reporterName": "Ravi Kumar",
  "reporterEmail": "ravi@example.com",
  "reporterPhone": "9876543210"
}
```

---

## Database Configuration

TraceBack uses a **file-based H2 database**, so reports and claims are kept after a restart.

```properties
spring.datasource.url=${TRACEBACK_DB_URL:jdbc:h2:file:./data/traceback_db}
spring.jpa.hibernate.ddl-auto=update
```

- The file `data\traceback_db.mv.db` is created in the folder **you start the app from**.
  Always start the app from the project folder so the same file is used each time.
- `ddl-auto=update` creates the tables on first run and keeps existing data afterwards.
- The `data/` folder is listed in `.gitignore` because it contains personal information.

**H2 console:** open `http://localhost:8080/h2-console`, log in as admin, then use
JDBC URL `jdbc:h2:file:./data/traceback_db`, user `sa` and an empty password
(or your `TRACEBACK_DB_PASSWORD`).

### Backing up the database

**Option 1 – copy the file (simplest)**
1. Stop the application (Ctrl + C).
2. Copy `data\traceback_db.mv.db` to a safe place, for example:
   ```bat
   mkdir backups
   copy data\traceback_db.mv.db backups\traceback_db_2026-11-10.mv.db
   ```
   (Put today's date in the file name so you can keep several backups.)
3. To restore, stop the app and copy the backup file back to `data\traceback_db.mv.db`.

**Option 2 – while the app is running**
Open the H2 console and run:
```sql
BACKUP TO 'backups/traceback_backup.zip';
```
This writes a consistent zipped copy without stopping the app.

> If you set `TRACEBACK_DB_PASSWORD`, set it **before the first run**. The password is
> fixed when the database file is created.

---

## Installation and Running on Windows

### Requirements
- **JDK 17 or newer**. Check with `java -version`.
- An internet connection on the first build (Maven downloads the dependencies).
- No separate Maven installation is needed: use `mvnw.cmd`.

### 1. Open Command Prompt in the project folder
```bat
cd C:\path\to\traceback
```

### 2. Run the tests
```bat
mvnw.cmd clean test
```

### 3. Build the JAR
```bat
mvnw.cmd clean package
```
This creates `target\traceback-0.0.1-SNAPSHOT.jar`.

### 4. Set the admin login (Command Prompt)
```bat
set TRACEBACK_ADMIN_USERNAME=admin
set TRACEBACK_ADMIN_PASSWORD=ChooseAStrongPassword123
```
PowerShell equivalent:
```powershell
$env:TRACEBACK_ADMIN_USERNAME="admin"
$env:TRACEBACK_ADMIN_PASSWORD="ChooseAStrongPassword123"
```
If you skip this step, the app prints a **temporary random admin password** in the
console at startup.

### 5. Start the application
```bat
java -jar target\traceback-0.0.1-SNAPSHOT.jar
```
Wait for `Tomcat started on port 8080`.

### 6. Open in the browser
| Page | URL |
|------|-----|
| Website | http://localhost:8080 |
| Admin dashboard | http://localhost:8080/admin.html (browser asks for the admin login) |
| H2 console | http://localhost:8080/h2-console |

To log out of the admin page, close all browser windows (HTTP Basic login is
remembered until the browser closes).

---

## Running the Tests

```bat
mvnw.cmd clean test
```

The test suite (42 test cases) uses its own in-memory database and a test-only admin
login, so it never touches your real `data` folder. It covers:

- creating reports, validation errors (400) and blocked fields such as `id`/`status`
- masking of contact details for the public and full details for the admin
- smart matching (opposite type only, RETURNED items ignored)
- claims: submit (201), duplicate (409), claim on LOST item (400), unknown report (404)
- admin-only actions without login (401) and with a wrong password (401)
- approving a claim marks the item RETURNED and rejects other pending claims
- reviewing the same claim twice (409)
- admin page and H2 console blocked without login
- converting a hosting provider's `DATABASE_URL` into PostgreSQL JDBC settings

---

## Screenshots

> Screenshots show sample test data only. To update them, save new images with the
> same file names in `docs/screenshots/`.

| Home page (desktop) | Admin dashboard (desktop) |
|---|---|
| ![Home page](docs/screenshots/home-desktop.png) | ![Admin dashboard](docs/screenshots/admin-desktop.png) |

| Home page (mobile) | Admin dashboard (mobile) |
|---|---|
| ![Home page mobile](docs/screenshots/home-mobile.png) | ![Admin mobile](docs/screenshots/admin-mobile.png) |

---

## Deploying to Vercel

Vercel does not recognise a Maven/Spring Boot project on its own; without a
container file it publishes an empty static site, which shows **404** even though the
deployment says *Ready*. This repository therefore contains:

- **`Dockerfile.vercel`**: builds the Spring Boot JAR with Maven and runs it on a
  Java 17 runtime image as a non-root user, with the `prod` profile switched on.
  Vercel gives this file priority over its own framework detection.
- **`vercel.json`**: sets `"framework": "container"` as an extra safeguard.

The whole app (website, admin page, CSS, JavaScript and API) is served by Spring Boot
from that one container, so `/` returns `index.html` exactly as it does locally.

### Environment variables (Vercel → Project → Settings → Environment Variables)

| Variable | Required | Example / notes |
|----------|----------|-----------------|
| `PORT` | **Yes** | `8080` — Vercel sends traffic to port 80 unless `PORT` is set; the container listens on 8080. |
| `TRACEBACK_ADMIN_PASSWORD` | **Yes** | A long, strong password. The app refuses to start in production without it. |
| `TRACEBACK_ADMIN_USERNAME` | No | Defaults to `admin`. |
| `DATABASE_URL` | **Yes**\* | `postgres://user:password@host/dbname?sslmode=require` (as given by Neon, Vercel Postgres or Supabase). |
| `TRACEBACK_DB_URL`, `TRACEBACK_DB_USERNAME`, `TRACEBACK_DB_PASSWORD` | \* | Alternative to `DATABASE_URL`: `jdbc:postgresql://host:5432/dbname?sslmode=require` plus username and password. |
| `TRACEBACK_DB_POOL_SIZE` | No | Database connections per instance, default `3`. |

\* Set **either** `DATABASE_URL` **or** the three `TRACEBACK_DB_*` variables. If no database
is configured, the app stops at startup with a clear error instead of using H2.

`SPRING_PROFILES_ACTIVE=prod` is already set inside `Dockerfile.vercel`.

### Things to know about this hosting model

- **Container runtime is in beta on Vercel.** Behaviour and limits may change.
- **Scale to zero:** when nobody visits for a while, Vercel stops the container. The next
  visitor waits while Spring Boot starts again (typically several seconds).
- **No local files survive:** that is why production uses PostgreSQL, never the H2 file.
- **Tables are created automatically** on the first start (`ddl-auto=update`).
- The **H2 console is disabled** in production.

### Run the production image locally (optional)

```bat
docker build -f Dockerfile.vercel -t traceback .
docker run -p 8080:8080 -e PORT=8080 -e TRACEBACK_ADMIN_PASSWORD=ChangeMe123 -e DATABASE_URL=postgres://user:password@host.docker.internal:5432/traceback traceback
```

---

## Future Enhancements

- User accounts and login for reporters and claimants, so people can track their own reports and claims.
- Email or SMS notifications when a match is found or a claim is reviewed.
- Image upload for items, and image-based matching.
- Admin login page with sessions and logout, multiple admin accounts and roles.
- Rate limiting and CAPTCHA on public forms to stop spam.
- Flyway database migrations instead of `ddl-auto=update` in production.
- Pagination for large numbers of reports and claims.
- QR-code tags for belongings that link directly to a TraceBack report.
- Health-check endpoint (Spring Boot Actuator) and monitoring.

---

## Author

Developed as the TraceBack – Smart Lost-and-Found Recovery System project.
