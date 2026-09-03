# CampusNav - Smart Campus Management & Route Optimization System

**Live application:** [https://campusnav-ys11.onrender.com](https://campusnav-ys11.onrender.com)  
**Source code:** [https://github.com/kiran-ys/CampusNav](https://github.com/kiran-ys/CampusNav)

> Render's free service can sleep after inactivity. The first request after sleeping may take up to approximately one minute.

CampusNav is a deployable Java smart-campus navigation system. Phase 1 provides the assessed console and DSA core, Phase 2 exposes it through a REST API, Phase 3 adds PostgreSQL persistence, Phase 4 provides the interactive web application, and Phase 5 packages the complete platform for automated testing and cloud deployment.

## Implemented features

- Add and view campus locations.
- Search by ID using `HashMap` lookup.
- Search by name using case-insensitive linear search.
- Add and view validated bidirectional routes.
- Find one reachable path using Breadth-First Search (BFS).
- Find the minimum-distance route using Dijkstra's algorithm and `PriorityQueue`.
- Display ordered locations, segment distances, and total distance.
- Recover safely from malformed menu and numeric input.
- Load representative demonstration data automatically.
- Run automated core, API, and PostgreSQL persistence test suites.
- Select either temporary in-memory storage or durable PostgreSQL storage.
- Create the database schema automatically and restore the Java graph after restart.
- Use a responsive Phase 4 web dashboard with an interactive SVG campus graph.
- Plan and highlight routes using either BFS or Dijkstra.
- Add persistent locations and routes from browser forms.

## Requirements

- JDK 17 or newer (developed and verified with JDK 21).
- No external libraries are required for in-memory mode.
- PostgreSQL mode uses the official PostgreSQL JDBC driver and Docker is recommended for local setup.

## Run

```bash
/bin/sh scripts/run.sh
```

## Run the REST API

```bash
/bin/sh scripts/api.sh
```

The API listens on `http://127.0.0.1:8080` by default. Configure it with:

- `CAMPUSNAV_PORT` - listening port
- `CAMPUSNAV_HOST` - listening address (defaults to loopback)
- `CAMPUSNAV_CORS_ORIGIN` - permitted browser origin

## Run with PostgreSQL (Phase 3)

First-time setup:

```bash
/bin/sh scripts/setup-postgres-driver.sh
docker compose up -d postgres
```

Start PostgreSQL, wait until it is healthy, and start the persistent API with one command:

```bash
/bin/sh scripts/api-postgres.sh
```

Keep that Terminal window open while using the API. The helper prevents a connection-refused error during PostgreSQL's first-time initialization.

Open the complete Phase 4 application at:

```text
http://127.0.0.1:8080
```

The frontend is served by the Java application itself, so no second web server or frontend installation is required.

The defaults connect to `jdbc:postgresql://127.0.0.1:5432/campusnav` with username and password `campusnav`. Override them with:

- `CAMPUSNAV_DB_URL`
- `CAMPUSNAV_DB_USER`
- `CAMPUSNAV_DB_PASSWORD`
- `CAMPUSNAV_DB_MIGRATE` (`true` by default)
- `CAMPUSNAV_SEED` (`true` by default; seeds only when the database is empty)

Data is stored in the Docker volume `campusnav_postgres_data` and therefore survives application and container restarts. The SQL blueprint is in `database/migrations/V1__create_campus_schema.sql`.

Key endpoints:

- `GET /api/health`
- `GET|POST /api/locations`
- `GET /api/locations/{id}`
- `GET /api/locations?name=library`
- `GET|POST /api/routes`
- `GET /api/routes/reachable?source=GATE-A&destination=CSE`
- `GET /api/routes/shortest?source=GATE-A&destination=CSE`

## Test

```bash
/bin/sh scripts/test.sh
```

With PostgreSQL running, test real database persistence and constraints:

```bash
/bin/sh scripts/test-postgres.sh
```

The integration suite clears only the dedicated `campusnav_test` database created by Docker; it never clears the normal `campusnav` development database. For an external test server, supply `CAMPUSNAV_TEST_DB_URL`, `CAMPUSNAV_TEST_DB_USER`, and `CAMPUSNAV_TEST_DB_PASSWORD`.

## Package

```bash
/bin/sh scripts/package.sh
java -jar dist/campusnav.jar
```

## Production deployment (Phase 5)

The repository includes:

- A multi-stage, non-root `Dockerfile`.
- A `render.yaml` Blueprint defining the web service and private PostgreSQL database.
- A GitHub Actions workflow that verifies Java, API, frontend, and database behavior.
- Health checks at `/api/health`.
- Database-aware health reporting and restrictive browser security headers.
- Secure administrator sessions for controlled online editing, with CSRF protection and login throttling.
- Coordinate-aware campus mapping, complete update/delete workflows, and persisted usage analytics.

Deployment workflow:

1. Create a GitHub repository and push this project.
2. In Render, select **New → Blueprint** and connect the repository.
3. When prompted, set a strong secret value for `CAMPUSNAV_ADMIN_PASSWORD`. Never commit it to Git.
4. Render reads `render.yaml`, creates the web service and PostgreSQL database, and injects database credentials securely.
5. After deployment, open the assigned `https://...onrender.com` URL and verify `/api/health`, administrator login, and logout.

Do not commit passwords or replace Blueprint database references with literal credentials.
The Render Blueprint enables controlled writes, but every create, update, delete, and analytics operation requires an authenticated administrator session. Anonymous visitors retain read-only route planning.

## Project structure

```text
src/main/java/com/campusnav/
  app/          Application entry point and sample data
  graph/        Adjacency-list graph, BFS and Dijkstra
  model/        Location, route, edge and path result models
  repository/   HashMap-backed location storage and search
  database/     PostgreSQL schema migration
  service/      Business rules and application use cases
  ui/           Menu-driven console interface
  validation/   Canonical ID and text validation
  api/          Java HttpServer, JSON and REST handlers

frontend/
  index.html    Accessible dashboard structure
  styles.css    Responsive visual system and graph styling
  app.js        Public API integration and SVG graph workflows
  admin.js      Secure login, CRUD, coordinate and analytics workflows
  admin.css     Administrator and analytics interface styles

src/test/java/com/campusnav/
  CampusNavTestSuite.java
  ApiIntegrationTestSuite.java
  PostgresIntegrationTestSuite.java
```

## Core design rules

- Location IDs are trimmed and normalized to uppercase.
- Required text fields cannot be blank.
- Location IDs must be unique; location names may repeat.
- Routes are bidirectional, unique by endpoint pair, and measured in positive integer metres.
- Self-routes and routes referencing missing locations are rejected.
- A valid but disconnected query returns a normal "no route exists" result.

## Algorithms

- **BFS:** checks connectivity and reconstructs one path. Complexity: `O(V + E)`.
- **Dijkstra:** calculates the minimum total distance for positive route weights. Complexity: `O((V + E) log V)` with an adjacency list and min-priority queue.

## API examples

```bash
curl http://127.0.0.1:8080/api/health
curl 'http://127.0.0.1:8080/api/routes/shortest?source=GATE-A&destination=CSE'
curl -X POST http://127.0.0.1:8080/api/locations \
  -H 'Content-Type: application/json' \
  -d '{"id":"AUD","name":"Main Auditorium","type":"AUDITORIUM","description":"Events venue"}'
```

Sample campus names and distances are representative and illustrative; they are not claimed to be official surveyed data.
