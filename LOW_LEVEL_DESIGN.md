# Course Registration Slice — Low-Level Design

## 1. Goal and boundaries

Implement enrollment, dropping, and waitlist promotion for one course offering in
the existing Spring Boot application. This is a small, runnable service slice,
not a complete registration platform.

Included: request validation, prerequisite eligibility checks, duplicate
prevention, capacity enforcement, FIFO promotion, and focused automated tests.

Excluded: frontend, course catalog, identity integrations, payments,
multi-term registration, deployment, and a separate external database.
Spring Data JPA with H2 provides the in-memory persistence implementation
for this assessment; no separate database or messaging infrastructure is used.

## 2. Assumptions and policy decisions

1. The application serves one configured offering with a positive capacity.
   Requests for any other offering are rejected.
2. A learner is eligible only when the prerequisite check says all required
   prerequisites are satisfied. The service checks eligibility before a new
   enrollment or waitlist entry and checks it again before promotion.
3. If a learner at the front of the waitlist is no longer eligible, that
   learner is **skipped for that promotion attempt and remains in the queue**.
   The scan continues and promotes the earliest eligible learner. A skipped
   learner is reconsidered at the next promotion attempt. This preserves queue
   history while preventing an ineligible learner from blocking eligible
   learners behind them.
4. An initially ineligible learner is neither enrolled nor waitlisted.
5. Repeated enrollment requests are idempotent: enrolled learners remain
   enrolled, and waitlisted learners retain their existing queue position.
   Repeated or invalid drops do not change state.
6. This first version has no trusted prerequisite system. The implementation
   will use a small local prerequisite-checking boundary suitable for the
   prototype; a production deployment must connect it to an authoritative
   source rather than trust client-supplied eligibility.
7. H2 in-memory data is lost on restart. Pessimistic JPA row locking and
   transaction boundaries coordinate mutations in this one-process,
   one-database assessment setup. They are not a multi-instance production
   persistence or coordination strategy.

## 3. Service boundary and components

```text
HTTP client
    |
    v
RegistrationController  -- request/response mapping and input validation
    |
    v
RegistrationService     -- enrollment/drop use cases and atomic state changes
    |
    +--> PrerequisiteEligibilityPolicy
    |
    v
RegistrationDao / JPA repositories -- offering and registration persistence
```

- **`RegistrationController`**: exposes the two mutation endpoints. It contains
  no queue or capacity rules.
- **`RegistrationService`**: implements the use cases and is the only
  application entry point that mutates the offering. Constructor-injects the
  offering state and prerequisite policy.
- **CourseOffering** / **CourseRegistration**: JPA entities own the persisted
  offering and learner-registration state. A composite registration key
  prevents duplicate offering/learner records.
- **`PrerequisiteEligibilityPolicy`**: answers whether a learner currently
  meets the offering's prerequisite rule. Keep this boundary small so the
  local prototype can later be replaced with a trusted prerequisite source.

Avoid separate repository/service layers that add no behavior to this
single-offering, in-memory slice.

## 4. Data and invariants

### Offering state

| Field | Type | Meaning |
|---|---|---|
| `offeringId` | string | ID of the sole supported offering |
| `capacity` | positive integer | Maximum number of enrolled learners |
| `enrolledLearnerIds` | insertion-ordered set of strings | Current enrollments |
| `waitlistedLearnerIds` | FIFO ordered collection of strings | Learners awaiting a seat |

### Invariants

- `0 <= enrolledLearnerIds.size() <= capacity`.
- A learner appears at most once across enrolled and waitlisted state.
- An enrolled learner is not on the waitlist.
- Queue order changes only when a learner is added or promoted/removed; skipping
  an ineligible learner does not reorder or remove that learner.
- Capacity is checked and state is changed atomically.
- A seat is never assigned to a learner who fails the prerequisite check at the
  time of enrollment or promotion.

Learner IDs are trimmed, non-empty strings with a documented maximum length.
Capacity must be greater than zero at application startup.

## 5. HTTP API contract

### 5.1 API list and resource naming

All routes are rooted at `/api/v1`. Use plural nouns for collections and
lowercase kebab-case if a multiword path is introduced. Use path segments for
resource identity and JSON for request/response bodies.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/offerings/{offeringId}` | Read offering capacity and aggregate counts |
| `POST` | `/api/v1/offerings/{offeringId}/enrollments` | Enroll learner, or add eligible learner to the FIFO waitlist |
| `DELETE` | `/api/v1/offerings/{offeringId}/enrollments/{learnerId}` | Drop an enrollment and promote one eligible waiter if possible |

This is the complete v1 API for the bounded slice. It deliberately does not
expose a roster or waitlist collection. That avoids returning learner lists
without an authentication/authorization design and keeps reads proportionate
to the feature.

### 5.2 Versioning and representation

- The major API version is in the URL (`/v1`). A breaking contract change
  requires a new major path such as `/v2`; additive optional response fields
  may be introduced in v1.
- Send `Accept: application/json`. The enrollment request must send
  `Content-Type: application/json`.
- JSON property names are lower camel case. Responses use stable string enums
  for statuses.
- A successful synchronous request returns the current result; do not use
  `202 Accepted` because waitlisting and promotion complete within the request.
- Send `Cache-Control: no-store` on responses because capacity and enrollment
  state can change immediately.

### 5.3 Read offering summary

`GET /api/v1/offerings/{offeringId}`

`200 OK`:

```json
{
  "offeringId": "course-101",
  "capacity": 30,
  "enrolledCount": 28,
  "availableSeats": 2,
  "waitlistedCount": 4
}
```

`waitlistedCount` includes retained learners currently skipped because they are
ineligible. This endpoint returns counts only, not learner identifiers.
Unknown offerings return `404 Not Found`.

### 5.4 Enroll or join the waitlist

`POST /api/v1/offerings/{offeringId}/enrollments`

Request:

```json
{ "learnerId": "learner-123" }
```

Eligibility is determined by `PrerequisiteEligibilityPolicy`; clients cannot
declare themselves eligible in the request.

`200 OK` for an enrolled or waitlisted result:

```json
{
  "offeringId": "course-101",
  "learnerId": "learner-123",
  "status": "WAITLISTED",
  "waitlistPosition": 2
}
```

`status` is `ENROLLED` or `WAITLISTED`. `waitlistPosition` is a one-based
position and is `null` when the learner is enrolled. It is a point-in-time
value, not a reservation or ordering guarantee after the response.

The same learner is the natural idempotency key within an offering. Repeating
this request never creates a second enrollment or queue entry and never changes
an existing waitlist position. The response describes the learner's current
state, so it may differ from an earlier response if the learner was promoted
between requests. A separate `Idempotency-Key` header and response replay store
are not needed for this state-based operation.

Errors: `400 Bad Request` for malformed/invalid IDs or JSON, `404 Not Found`
for an unknown offering, and `422 Unprocessable Content` with code
`PREREQUISITE_NOT_MET` if the learner is ineligible. Errors leave state
unchanged.

### 5.5 Drop enrollment

`DELETE /api/v1/offerings/{offeringId}/enrollments/{learnerId}`

`200 OK`:

```json
{
  "offeringId": "course-101",
  "droppedLearnerId": "learner-123",
  "promotedLearnerId": "learner-456",
  "status": "DROPPED"
}
```

`promotedLearnerId` is `null` if no eligible waitlisted learner was found.
The response identifies the single learner promoted, if any; it does not return
the waitlist.

The `DELETE` operation is idempotent with respect to state: a repeated request
cannot remove another learner or trigger another promotion. If the learner is
no longer enrolled, return `404 Not Found` with code
`ENROLLMENT_NOT_FOUND`; the waitlist and capacity remain unchanged. Dropping a
waitlisted learner is not supported by this endpoint.

### 5.6 Validation rules

- `offeringId` and `learnerId` are opaque path identifiers matching
  `^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$`; reject blank, oversized, malformed, or
  unencoded path values. Do not silently trim or normalize identifiers.
- `learnerId` is required. Reject missing/null values, invalid JSON, and
  unknown request properties with a field-level validation error.
- Capacity is a positive integer validated at startup.
- Validate before mutating state. Any validation or eligibility error must
  leave enrollment and waitlist state unchanged.
- Never trust a client-provided eligibility flag; eligibility is a server-side
  policy decision.

### 5.7 Errors and request tracing

Use the standard `application/problem+json` problem-details shape (RFC 9457)
with stable extension code:

```json
{
  "type": "about:blank",
  "title": "Enrollment not found",
  "status": 404,
  "detail": "The learner is not enrolled in this offering.",
  "instance": "/api/v1/offerings/course-101/enrollments/learner-123",
  "code": "ENROLLMENT_NOT_FOUND"
}
```

Use stable codes such as `INVALID_REQUEST`, `OFFERING_NOT_FOUND`,
`PREREQUISITE_NOT_MET`, and `ENROLLMENT_NOT_FOUND`. Do not expose stack traces,
internal class names, or sensitive implementation details. Accept an optional
`X-Request-Id`; otherwise generate one, return it in the same response header,
and include it in server logs for correlation. It is not an idempotency token.

### 5.8 Pagination, sorting, and filtering

V1 exposes no collection endpoint, so pagination, sorting, and filtering do not
apply. The offering summary contains counts rather than unbounded arrays.
If a later requirement adds a waitlist/roster collection, use cursor pagination
(`limit` with a documented maximum and an opaque `cursor`) over a stable queue
sequence; do not use unbounded responses or promise offset pagination over a
mutating FIFO queue. That later endpoint also requires an authorization and
privacy decision.

### 5.9 Other API concerns

- Do not add broad CORS permissions. Configure allowed origins only if a
  browser client is introduced.
- Authentication, authorization, rate limiting, and audit retention are not
  implemented in this slice. The API is a local prototype, not safe to expose
  publicly until those concerns and trusted prerequisite data are addressed.
- Keep error responses consistent and never return success-shaped fallbacks
  for unexpected failures.

## 6. Use-case algorithms

### Enroll

Within the atomic state-change boundary:

1. Validate the offering ID and learner ID.
2. If the learner is already enrolled, return `ALREADY_ENROLLED`.
3. If already waitlisted, return `ALREADY_WAITLISTED` without changing queue
   position.
4. Check the current prerequisite eligibility. If false, return
   `PREREQUISITE_NOT_MET` and leave state unchanged.
5. If there is a free seat and no older waitlisted learner, enroll the learner.
6. Otherwise append the learner once to the end of the waitlist. If a seat is
   currently open, run the FIFO eligibility scan; this can enroll an older
   eligible learner or the new learner if all older entries are ineligible.
7. Return the new learner's resulting status (`ENROLLED` or `WAITLISTED`).

### Drop and promote

Within the same atomic state-change boundary:

1. Validate the offering ID and learner ID.
2. If the learner is not enrolled, return `404 Not Found` with no state change.
3. Remove the learner from enrolled state.
4. Traverse the waitlist in arrival order. For each learner, re-check
   eligibility. Leave ineligible learners in their original positions and
   continue scanning.
5. Remove and enroll the first eligible learner, if any. Stop after one
   promotion.
6. If no eligible learner exists, keep the seat open and preserve the queue.
7. Return the drop and optional promotion result.

Each opened seat produces at most one promotion. A later drop or explicit
promotion trigger re-evaluates skipped learners.

## 7. Concurrency and persistence

Use a JPA transaction to lock the `CourseOffering` row with
`PESSIMISTIC_WRITE` before capacity and queue mutations. Keep the lock around
duplicate checks, eligibility evaluation, capacity checks, queue edits, and
promotion so concurrent requests cannot oversubscribe capacity or promote the
same learner twice.

This is deliberately scoped to one JVM and one H2 database instance. A later
horizontally scaled deployment must use an external transactional database,
review database-specific locking behavior, and add retry/timeout coverage.
The H2 setup is appropriate for the assessment and is not a production
persistence strategy.

## 8. Error handling

- Invalid/blank/oversized learner ID or malformed request: `400 Bad Request`;
  no state change.
- Unknown offering: `404 Not Found`; no state change.
- Prerequisite failure: `422 Unprocessable Content`; no enrollment or queue
  insertion.
- Drop for a learner who is not enrolled: `404 Not Found`; no promotion.
- Duplicate enrollment: successful idempotent response reflecting existing
  state; no duplicate record or queue-position change.
- Unexpected errors should be logged by the normal application error handling
  path and must not be converted into a success-shaped response.

## 9. Focused test plan

Use the existing JUnit/Spring test dependencies. Prefer direct service tests
with a deterministic prerequisite-policy test double; add a small controller
test if HTTP mapping needs verification.

| Case | Expected assertion |
|---|---|
| Eligible learner, free capacity | Learner is enrolled |
| Eligible learner, full offering | Learner is appended to waitlist |
| Repeated enrollment while enrolled | No duplicate; remains enrolled |
| Repeated enrollment while waitlisted | One queue entry; original FIFO position retained |
| Drop with eligible queue | Earliest eligible learner is promoted |
| Ineligible learner at queue head | Skipped and retained; next eligible learner promoted |
| No eligible learner on queue | Drop succeeds; seat remains open |
| Initially ineligible enrollment | Not enrolled or waitlisted |
| Invalid learner/offering input | Clear error; state unchanged |
| Drop by non-enrolled learner, including repeated drop | Clear not-found result; state unchanged |
| Concurrent enrollments at final seat | At most capacity learners enrolled; no duplicate learner state |

For each mutation test, assert both the response and resulting enrolled/waitlist
state so failure handling cannot silently corrupt the offering.

## 10. Design principles

- **SOLID:** controller/use-case/state/eligibility responsibilities are
  separated; the service depends on a small eligibility abstraction. Avoid
  speculative extension points beyond the prerequisite boundary.
- **KISS:** one offering, three endpoints, JPA persistence, no new
  infrastructure or policy engine.
- **DRY:** centralize eligibility, duplicate, and promotion rules in the
  service/state boundary; do not repeat them in controllers.

## 11. Known limitations and follow-up risks

- In-memory state resets on restart and cannot coordinate multiple application
  instances.
- A local prerequisite checker is only a prototype seam. Production must use
  an authoritative source and define how eligibility changes are observed.
- No authentication/authorization or rate limiting is included because identity
  integration is explicitly out of scope.
- There is no queue inspection endpoint; responses and tests are the initial
  verification surface.
