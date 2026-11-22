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
- focused service integration and concurrent boundary tests;
- versioned REST APIs, request tracing, and RFC 9457 problem responses.

Not included: frontend, catalog, identity integration, payments, multi-term
registration, deployment automation, Kafka, or an external database.

## 2. Important assumptions

- The offering is `course-101`.
- Default capacity is `2`.
- Learners `learner-1` through `learner-5` are eligible by default.
- The list is Java-backed demo configuration. No PowerShell data setup is
  required.
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

## 5. Panel-member setup instructions

### Step 1: Install Java

Install a JDK 21 distribution. Eclipse Temurin 21 is suitable on Windows.

Verify:

```powershell
java -version
javac -version
```

Both commands must report Java 21.

### Step 2: Open the project

Open this repository folder in Visual Studio Code or another Java IDE:

```text
C:\Users\DELL\Downloads\registration
```

Install/enable the VS Code Java language support and debugger extensions only
if the IDE prompts for them.

### Step 3: Configure Java for the current terminal

Find Java 21:

```powershell
Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory
```

Set it for the current PowerShell session using the directory reported by the
previous command:

```powershell
$jdk = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.10002-hotspot"
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"
java -version
```

The exact JDK folder can differ. Use the folder that exists on the panel
member's machine.

### Step 4: Run tests

From the repository root:

```powershell
.\mvnw.cmd test
```

A successful run finishes with `BUILD SUCCESS`.

### Step 5: Start the application

```powershell
.\mvnw.cmd spring-boot:run
```

Wait until the log contains `Started RegistrationApplication`.

The service address is:

```text
http://localhost:8080
```

No PowerShell environment variables or seed commands are needed. The offering
and eligible learner collection are initialized from Java configuration.

Optional environment overrides:

```powershell
$env:REGISTRATION_OFFERING_ID="course-101"
$env:REGISTRATION_CAPACITY="2"
.\mvnw.cmd spring-boot:run
```

The environment variable names do not enable eligible-learner overrides.
Demo eligibility is controlled by the Java collection in
`RegistrationProperties`.

### Step 6: Run Postman scenarios

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

Run the complete suite:

```powershell
.\mvnw.cmd clean test
```

Build the executable artifact:

```powershell
.\mvnw.cmd clean package
```

Run the packaged application:

```powershell
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
