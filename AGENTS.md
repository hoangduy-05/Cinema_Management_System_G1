# Repository guide

## Boundaries

There is no root build or workspace command. Run commands inside the owning project:

- `backend/`: Java 21, Spring Boot 3.4.5, Maven; requires a global `mvn` because no wrapper is committed.
- `frontend/`: React 18, Vite 5, npm; use the committed `package-lock.json`.
- `database/scripts.sql`: destructive MySQL schema/seed dump; it drops and recreates `cinema_management`.

## Backend

```powershell
cd backend
mvn spring-boot:run
mvn test
mvn clean package
mvn -Dtest=BookingStateMachineTest test
mvn "-Dtest=BookingStateMachineTest#terminalStatesCannotTransition" test
```

Import `database/scripts.sql` before first normal startup. Runtime uses MySQL `cinema_management` on localhost:3306. Hibernate is validation-only; Flyway baselines existing schemas at version 0 and runs migrations from `backend/src/main/resources/db/migration`. Add incremental migrations rather than editing applied migrations or relying on Hibernate to alter tables.

`backend/src/main/resources/application.properties` currently hard-codes `root` / `1234`; the README's `DB_USERNAME` and `DB_PASSWORD` variables are not bound there. The properties file is also listed in `backend/.gitignore`, so check tracking before editing it.

Without `JWT_SECRET`, startup generates an ephemeral key and existing JWTs die on restart. Payment callbacks remain disabled until `BOOKING_PAYMENT_SIMULATION_TOKEN` is set. Swagger UI is at `http://localhost:8080/swagger-ui/index.html`.

Booking state changes must go through `BookingStateMachine`. Preserve stable-ID pessimistic seat-lock ordering and server-side pricing. Payment confirmation books seats and issues tickets transactionally; ticket delivery runs through an `AFTER_COMMIT` event.

Backend integration tests use H2 in MySQL mode with Flyway disabled; tests do not require local MySQL.

## Frontend

Locked Vite supports Node `^18.0.0 || >=20.0.0`.

```powershell
cd frontend
npm ci
npm run dev
npm run build
npm run preview
```

Development runs on port 5173. API calls default to `/api/v1`, and Vite proxies `/api` to Spring Boot on `http://localhost:8080`; `VITE_API_BASE_URL` overrides the base URL.

Requests should use `src/api/client/axiosClient.js` and endpoint modules under `src/api/services`. The client adds the stored JWT, clears it on HTTP 401, and unwraps the backend `{success,message,data,timestamp}` envelope.

Seat holds submit `showtimeSeatId`, not physical `seatId`. Treat server-returned prices and deadlines as authoritative; the seat-map hook polls every 10 seconds.

`npm run mock` starts `frontend/server.js` on 8081, but its routes and response envelopes are older than the current client contract, while Vite still proxies to 8080. It is not a drop-in Spring backend replacement.

## Verification gaps

There are no repository-defined frontend test, lint, format, typecheck, or codegen commands, and no configured backend lint, formatter, or codegen task. Do not run nonexistent scripts such as `npm test`, `npm run lint`, or `npm run typecheck`.
