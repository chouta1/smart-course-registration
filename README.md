# Smart Course Registration

This bounded Spring Boot 4 / Java 21 service provides enrollment, dropping,
and FIFO waitlist promotion for one configured course offering.

## 1. Features and scope

The implementation includes:

- available-seat enrollment;
- one-record-per-learner enrollment and waitlist prevention;
- FIFO promotion of the earliest eligible waiter;
- skip-and-retain behavior for an ineligible waiter;
- invalid identifier, malformed JSON, missing offering, prerequisite failure,
  and repeated-drop handling;
- transactional JPA mutations and pessimistic locking;
- controller and service unit tests, DAO adapter tests, H2/JPA and HTTP
  integration tests, and concurrent boundary tests;
- a JaCoCo line-coverage gate of 90% for `mvn verify`;
- versioned REST APIs, request tracing, and RFC 9457 problem responses.

Not included: frontend, catalog, identity integration, payments, multi-term
registration, deployment automation, Kafka, or an external database.

## 2. Important assumptions

- The offering is `course-101`.
- Default capacity is `2`.
- Learners `learner-1` through `learner-5` are eligible by default.
- The list is Java-backed demo configuration. No manual data setup is required.
- An initially ineligible learner is neither enrolled nor waitlisted.
- If an ineligible learner reaches the front during promotion, the service
  skips that learner, promotes the earliest eligible later learner, and leaves
  the skipped learner at the existing queue position.
- A repeated enrollment request returns the learner's current state and never
  creates a duplicate or changes the waitlist position.
- A repeated drop returns `ENROLLMENT_NOT_FOUND` and performs no promotion.
- H2 is an in-memory database; restarting the application clears all
  registrations and waitlist entries.

## 3. Technology

- Java 21
- Spring Boot 4.2
- Spring Web MVC
- Spring Validation
- Spring Data JPA / Hibernate
- H2 in-memory database
- JUnit 5 and Spring Boot test support
- Maven Wrapper

No Spring DevTools or Kafka dependency is used.

## 4. Project structure

```text
src/main/java/com/smartcourse/registration
  config/          Configuration properties and startup offering initialization
  controller/      Versioned REST mappings
  dao/             JPA-backed application DAO adapters
  dto/             Request and response API models
  entity/          JPA entities and embedded keys
  exception/       Domain exceptions
  filter/          Request ID filter
  handler/         RFC 9457 exception handling
  repository/      Spring Data JPA repositories
  service/         Use cases and prerequisite policy
  service/impl/    Use-case implementation
  domain/          Immutable results and shared domain types
```

## 5. Panel-member setup instructions (VS Code)

### Step 1: Check Java and Maven

The panel member's system should have Java 21 and Maven installed and available
in the terminal `PATH`. In the VS Code integrated terminal, confirm both are
available:

```text
java -version
mvn -version
```

Java must report version 21.

### Step 2: Open the project and integrated terminal

In VS Code, select **File > Open Folder...** and open the cloned repository.

Install/enable the VS Code Java language support and debugger extensions only
if the IDE prompts for them.

Open **Terminal > New Terminal**. Run the following commands from the
repository root (the folder containing `pom.xml`).

### Step 3: Run tests

```text
mvn test
```

A successful run finishes with `BUILD SUCCESS`.

To also generate the JaCoCo report and enforce the 90% line-coverage gate:

```text
mvn verify
```

The HTML report is written to `target\site\jacoco\index.html`.

### Step 4: Start the application

```text
mvn spring-boot:run
```

Wait until the log contains `Started RegistrationApplication`.

The service address is:

```text
http://localhost:8080
```

No environment variables or seed commands are needed. The offering and
eligible learner collection are initialized from Java configuration.

### Step 5: Run Postman scenarios

Open [POSTMAN.md](POSTMAN.md). Import is not required; create a collection
manually or follow the numbered requests. Start with the summary and then
exercise capacity, duplicates, waitlisting, prerequisite rejection, promotion,
invalid input, and repeated drop scenarios.

## 6. API list

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/offerings/{offeringId}` | Read capacity and counts |
| `POST` | `/api/v1/offerings/{offeringId}/enrollments` | Enroll or waitlist a learner |
| `DELETE` | `/api/v1/offerings/{offeringId}/enrollments/{learnerId}` | Drop an enrollment and promote one eligible waiter |

Request and response examples are in [POSTMAN.md](POSTMAN.md).

## 7. Verification commands

Run these commands from the VS Code integrated terminal at the repository
root. They use Maven installed on the panel member's system.

Run the complete suite with coverage enforcement:

```text
mvn clean verify
```

Build the executable artifact:

```text
mvn clean package
```

Run the packaged application:

```text
java -jar target\registration-0.0.1-SNAPSHOT.jar
```

## 8. Limitations and follow-up risks

- H2 data is in-memory and resets after restart.
- The prerequisite policy is a Java demo collection, not an authoritative
  course-prerequisite system.
- No authentication or authorization is included, because identity integration
  is outside the assessment scope.
- Pessimistic JPA locking protects one running database instance. A later
  horizontally scaled deployment requires reviewed database/transaction
  behavior, retry handling, and a supported external database.
- No collection endpoint or pagination is implemented because it would add
  learner privacy and authorization scope beyond the bounded feature.

Additional design details are in [LOW_LEVEL_DESIGN.md](LOW_LEVEL_DESIGN.md).
