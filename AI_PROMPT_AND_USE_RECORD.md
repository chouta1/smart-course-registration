# AI Prompt and Use Record

This file records AI-assisted engineering activity for the assessment. It
contains no credentials or sensitive identifiers.

## Engineering activities

### Principal prompts used

These prompts are copied or conservatively reconstructed from the assessment
conversation and describe the actual engineering requests made to the AI.
They contain no credentials or sensitive data.

1. "Generate the low-level design for an implementation that supports
   enrollment, dropping, and FIFO waitlist promotion for one course offering.
   Define prerequisite assumptions and whether an ineligible learner is skipped
   or retained."

2. "Now generate API contracts following real API design principles: naming,
   versioning, pagination, validation, idempotency, error handling, and
   request tracing."

3. "First generate the complete codebase. Use production-ready Java/Spring
   Boot conventions and cover controller, service, DAO, repositories, entities,
   DTOs, configuration, exceptions, handler, filter, and test layers."

4. "Use Spring Data JPA instead of legacy JdbcTemplate/JDBC style. Remove
   Spring DevTools and do not use Kafka or other messaging infrastructure."

5. "Separate the project into conventional controller, service, service/impl,
   repository, dao, dto, entity, config, exception, handler, and filter
   packages, like a real Spring Boot project."

6. "The offering ID, capacity, and eligible learner IDs should be created in
   Java collections/configuration rather than requiring panel members to run
   PowerShell seed commands."

7. "Create POSTMAN.md containing the ordered APIs to test, sample request
   bodies, expected results, and negative scenarios including ineligible
   learners, repeated drops, invalid input, and locking."

8. "Update the README with step-by-step setup, test, run, API, limitation,
   and panel-member instructions."

## Follow-up prompts used

- "The build is failing on Java; use Java 21 and fix the current compilation
  or test failures."
- "The FIFO promotion test is treating learner-3 as ineligible before it was
  waitlisted. Correct the test setup."
- "Remove the duplicate test field and stale package/import references."
- "Correct the design documentation so it describes JPA/H2 accurately instead
  of process-memory state."

| Prompt | AI response summary | Disposition | Verification |
|---|---|---|---|
| "Generate a bounded LLD for enrollment, drop, FIFO waitlist promotion, prerequisites, concurrency, and tests." | Suggested the service boundary, state invariants, skip-ineligible-and-retain FIFO policy, API shape, and test matrix. | Edited: the final design uses Spring Data JPA and H2 rather than process memory, and excludes collection endpoints. | Compared every invariant with the implementation and test cases. |
| "Generate API contracts for naming, versioning, validation, idempotency, pagination, and errors." | Suggested `/api/v1/offerings/{offeringId}` routes, Problem Details, stable codes, request tracing, and cursor pagination only for future collections. | Accepted with edits: the final route uses `/enrollments`; no paginated collection exists in v1. | Contract reviewed against controller mappings and exception handler. |
| "Convert the implementation to Spring Data JPA and remove DevTools and JDBC-style code." | Proposed JPA entities, repositories, pessimistic offering locking, transactional DAO, and H2 configuration. | Accepted after review; added composite learner/offering identity and explicit FIFO `queue_sequence`. | Source scan, repository review, and focused integration tests. |
| "Reorganize into controller, service, dao/repository, dto, entity, config, exception, handler, and filter packages." | Proposed conventional package names and interface/implementation separation. | Accepted: kept `RegistrationService` as an interface and `RegistrationServiceImpl` as implementation, while rejecting unnecessary application/persistence abstractions. | Package/import scan; application and test compilation. |
| "Use Java collections to seed demo offering, capacity, and eligible learners; remove PowerShell-only setup." | Proposed Java-backed property defaults and startup offering initialization. | Accepted; removed environment-variable setup for learner eligibility and retained only optional offering/capacity overrides. | Verified default collection in `RegistrationProperties`, startup initializer, and README run steps. |
| "Create ordered Postman scenarios and update README for panel members." | Proposed a restartable 18-step request sequence covering positive, negative, FIFO, repeated-drop, and locking evidence. | Accepted and edited to match the final `/api/v1/offerings` routes and Java-backed seed data. | Reviewed each scenario against controller mappings, service outcomes, and problem-detail codes. |

## Independent review performed

The implementation was reviewed independently for:

- duplicate enrollment and waitlist records;
- capacity never exceeding the configured limit;
- earliest eligible waiter promotion;
- ineligible waiters retained without reordering;
- repeated drops without state changes;
- invalid identifiers and malformed JSON;
- transaction and pessimistic-lock behavior;
- stale JDBC/DevTools/Kafka references;
- route and package consistency.

## Verification evidence

Run after implementation:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

The test suite includes available capacity, full capacity, duplicate requests,
FIFO eligible promotion, prerequisite failure, repeated/invalid drops,
concurrent boundary enrollment, and HTTP problem-details validation.
