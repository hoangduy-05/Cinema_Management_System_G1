# Cinema Management System

Spring Boot backend for browsing cinema schedules and completing a customer booking with temporary seat holds, server-side pricing, simulated payment, tickets, and automatic expiration.

## Local setup

The application uses MySQL database `cinema_management`. Run the supplied base schema and seed scripts first, then start the application with the database credentials in environment variables:

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your-password"
$env:JWT_SECRET="a-long-base64-encoded-secret"
$env:BOOKING_PAYMENT_SIMULATION_TOKEN="a-long-random-local-test-token"
mvn spring-boot:run
```

For local browsing, registration and login, the application can start without
`JWT_SECRET`; it generates an ephemeral development key and existing JWTs become
invalid after each restart. The simulated payment callback stays disabled until
`BOOKING_PAYMENT_SIMULATION_TOKEN` is configured. Production deployments must
set both values explicitly.

Flyway baselines an existing schema at version `0` and applies the booking lifecycle migration. Hibernate uses `ddl-auto=validate`; it does not create or update production tables.

Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`.

## Booking lifecycle

```text
CREATED
  -> SEAT_HELD
      -> PENDING_PAYMENT
          -> CONFIRMED
              -> COMPLETED
          -> CANCELLED
          -> EXPIRED
      -> CANCELLED
      -> EXPIRED
```

A failed payment attempt keeps the booking in `PENDING_PAYMENT` until the payment deadline, allowing the customer to retry. Seats remain held during this period. A timeout changes the booking to `EXPIRED`; an explicit customer cancellation changes it to `CANCELLED`.

No transition is allowed from `COMPLETED`, `CANCELLED`, or `EXPIRED`. All status changes are validated by the central `BookingStateMachine`.

## Public browsing APIs

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/v1/movies` | List active movies |
| `GET` | `/api/v1/movies/{movieId}` | Movie details |
| `GET` | `/api/v1/showtimes/dates` | Dates that have available showtimes |
| `GET` | `/api/v1/branches` | Active cinema branches |
| `GET` | `/api/v1/showtimes?movieId=&date=&branchId=` | Browse future showtimes |
| `GET` | `/api/v1/showtimes/{showtimeId}` | Showtime details |
| `GET` | `/api/v1/showtimes/{showtimeId}/seats` | Seat map, calculated prices and availability |
| `GET` | `/api/v1/movies/{movieId}/showtimes?date=&branchId=` | Showtime groups by branch |
| `GET` | `/api/v1/branches/{branchId}/showtimes/quick?date=` | Quick schedule grouped by movie |
| `GET` | `/api/v1/combos` | List active concession combos |

## Authentication APIs

| Method | Endpoint | Authentication |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Public |
| `POST` | `/api/v1/auth/login` | Public |

Use the returned access token as `Authorization: Bearer <token>` for customer booking APIs.

## Customer booking APIs

| Method | Endpoint | Required status | Status after success |
|---|---|---|---|
| `POST` | `/api/v1/bookings/holds` | New booking | `SEAT_HELD` |
| `PUT` | `/api/v1/bookings/{bookingId}/combos` | `SEAT_HELD` | `SEAT_HELD` |
| `POST` | `/api/v1/bookings/{bookingId}/voucher` | `SEAT_HELD` | `SEAT_HELD` |
| `DELETE` | `/api/v1/bookings/{bookingId}/voucher` | `SEAT_HELD` | `SEAT_HELD` |
| `GET` | `/api/v1/bookings/me?page=0&size=10&status=` | Customer JWT; optional status filter | Unchanged |
| `GET` | `/api/v1/bookings/{bookingId}/summary` | Owner only | Unchanged |
| `POST` | `/api/v1/bookings/{bookingId}/checkout` | `SEAT_HELD` | `PENDING_PAYMENT` |
| `POST` | `/api/v1/bookings/{bookingId}/payments/retry` | `PENDING_PAYMENT` | `PENDING_PAYMENT` |
| `POST` | `/api/v1/bookings/{bookingId}/cancel` | `SEAT_HELD` or `PENDING_PAYMENT` | `CANCELLED` |

Creating a hold returns HTTP `201`. Other successful booking operations return HTTP `200`. The customer identity always comes from the JWT principal; request bodies never accept `customerId`.

## Payment APIs

| Method | Endpoint | Authentication | Required booking status | Status after success |
|---|---|---|---|---|
| `POST` | `/api/v1/payments` | Customer JWT | `PENDING_PAYMENT` | `PENDING_PAYMENT` |
| `GET` | `/api/v1/payments/booking/{bookingId}/latest` | Customer JWT; owner only | Any status with a payment attempt | Unchanged |
| `POST` | `/api/v1/payments/{paymentId}/retry` | Customer JWT | `PENDING_PAYMENT` | `PENDING_PAYMENT` |
| `POST` | `/api/v1/payments/{paymentId}/browser-confirm` | Customer JWT; owner only | `PENDING_PAYMENT`; browser simulation enabled | `CONFIRMED` |
| `POST` | `/api/v1/payments/callback` | Simulation token | `PENDING_PAYMENT` | `CONFIRMED` for payment success; unchanged for a failed attempt before deadline |

The development payment gateway is isolated behind the payment service. Configure its callback verification token with `BOOKING_PAYMENT_SIMULATION_TOKEN` and send that operator-known value only in the callback request. It is never returned by a customer payment response. A production gateway must replace this simulation and perform its own cryptographic signature verification.

## Concurrency and idempotency

- Requested showtime-seat rows are locked with `PESSIMISTIC_WRITE` in stable ID order. The whole hold is committed atomically; partial reservation is not possible.
- Booking, payment, and limited voucher updates use row locks plus optimistic `@Version` fields.
- Repeated checkout returns an existing valid pending attempt.
- A repeated successful callback does not issue duplicate tickets or consume a voucher twice.
- Ticket and transaction-reference unique constraints provide database-level protection.
- Expiration and completion scans use bounded batches and re-lock each booking before changing it.
