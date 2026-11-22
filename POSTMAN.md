# Postman Course Registration Test Guide

Use the base URL:

```text
http://localhost:8080
```

Create an environment variable named `baseUrl` with:

```text
http://localhost:8080
```

Create an environment variable named `offeringId` with:

```text
course-101
```

Use `POST`, `GET`, and `DELETE` as shown. Execute the requests in the exact
order below to demonstrate capacity, duplicate prevention, FIFO waitlisting,
prerequisite rejection, promotion, invalid input, locking, and repeated drop
behavior.

Default offering state after startup:

- offering: `course-101`
- capacity: `2`
- eligible learners: `learner-1` through `learner-5`
- enrolled: none
- waitlisted: none

Restart the application before re-running the whole sequence.

## 1. Initial offering summary

**Purpose:** prove the offering starts with the configured capacity and no
registrations.

**Request**

- Method: `GET`
- URL: `{{baseUrl}}/api/v1/offerings/{{offeringId}}`
- Body: none

**Expected**

- Status: `200 OK`
- `capacity`: `2`
- `enrolledCount`: `0`
- `availableSeats`: `2`
- `waitlistedCount`: `0`

## 2. Enroll learner-1

**Request**

- Method: `POST`
- URL: `{{baseUrl}}/api/v1/offerings/{{offeringId}}/enrollments`
- Header: `Content-Type: application/json`
- Body:

```json
{
  "learnerId": "learner-1"
}
```

**Expected**

- Status: `200 OK`
- `status`: `ENROLLED`
- `waitlistPosition`: `null`

## 3. Enroll learner-2

**Request**

- Method: `POST`
- URL: `{{baseUrl}}/api/v1/offerings/{{offeringId}}/enrollments`
- Header: `Content-Type: application/json`
- Body:

```json
{
  "learnerId": "learner-2"
}
```

**Expected**

- Status: `200 OK`
- `status`: `ENROLLED`
- capacity is now full

## 4. Duplicate enrollment

Repeat request 2:

```json
{
  "learnerId": "learner-1"
}
```

**Expected**

- Status: `200 OK`
- `status`: `ENROLLED`
- only one learner-1 record exists
- the second request does not add or move anything

## 5. First eligible waitlist entry

**Request**

- Method: `POST`
- URL: `{{baseUrl}}/api/v1/offerings/{{offeringId}}/enrollments`
- Body:

```json
{
  "learnerId": "learner-3"
}
```

**Expected**

- Status: `200 OK`
- `status`: `WAITLISTED`
- `waitlistPosition`: `1`

## 6. Duplicate waitlist entry

Send the learner-3 body again.

**Expected**

- Status: `200 OK`
- `status`: `WAITLISTED`
- `waitlistPosition`: `1`
- no duplicate queue record

## 7. Second eligible waitlist entry

Send:

```json
{
  "learnerId": "learner-4"
}
```

**Expected**

- `status`: `WAITLISTED`
- `waitlistPosition`: `2`

## 8. Ineligible learner rejection

For this assessment policy, `learner-99` is not in the Java-backed eligible
learner collection.

**Request**

- Method: `POST`
- URL: `{{baseUrl}}/api/v1/offerings/{{offeringId}}/enrollments`
- Body:

```json
{
  "learnerId": "learner-99"
}
```

**Expected**

- Status: `422 Unprocessable Entity`
- Content-Type: `application/problem+json`
- `code`: `PREREQUISITE_NOT_MET`
- learner-99 is neither enrolled nor waitlisted
- no capacity or queue position changes

## 9. Invalid body

**Request**

- Method: `POST`
- URL: `{{baseUrl}}/api/v1/offerings/{{offeringId}}/enrollments`
- Body:

```json
{
  "learnerId": ""
}
```

**Expected**

- Status: `400 Bad Request`
- `code`: `INVALID_REQUEST`
- `errors` contains the learner ID validation result
- state is unchanged

## 10. Malformed JSON

**Request**

- Method: `POST`
- URL: `{{baseUrl}}/api/v1/offerings/{{offeringId}}/enrollments`
- Body:

```json
{"learnerId":
```

**Expected**

- Status: `400 Bad Request`
- `code`: `INVALID_REQUEST`
- no state change

## 11. Unknown offering

**Request**

- Method: `GET`
- URL: `{{baseUrl}}/api/v1/offerings/unknown-offering`

**Expected**

- Status: `404 Not Found`
- `code`: `OFFERING_NOT_FOUND`

## 12. Drop learner-1 and demonstrate FIFO promotion

**Request**

- Method: `DELETE`
- URL: `{{baseUrl}}/api/v1/offerings/{{offeringId}}/enrollments/learner-1`

**Expected**

- Status: `200 OK`
- `droppedLearnerId`: `learner-1`
- `promotedLearnerId`: `learner-3`
- learner-2 remains enrolled
- learner-4 remains second in the waitlist

## 13. Summary after promotion

**Request**

- Method: `GET`
- URL: `{{baseUrl}}/api/v1/offerings/{{offeringId}}`

**Expected**

- `capacity`: `2`
- `enrolledCount`: `2`
- `availableSeats`: `0`
- `waitlistedCount`: `1`

## 14. Repeat the successful drop

Send the request from step 12 again.

**Expected**

- Status: `404 Not Found`
- `code`: `ENROLLMENT_NOT_FOUND`
- no second promotion occurs
- learner-4 remains waitlisted

## 15. Drop learner-2 and promote the next waiter

**Request**

- Method: `DELETE`
- URL: `{{baseUrl}}/api/v1/offerings/{{offeringId}}/enrollments/learner-2`

**Expected**

- `promotedLearnerId`: `learner-4`
- capacity remains at two enrolled

## 16. Repeated waitlist/drop edge

Attempt to drop learner-3 by using the same drop endpoint.

**Request**

- Method: `DELETE`
- URL: `{{baseUrl}}/api/v1/offerings/{{offeringId}}/enrollments/learner-3`

**Expected**

- Status: `404 Not Found`
- `code`: `ENROLLMENT_NOT_FOUND`
- waitlist and enrollment state do not change

## 17. Invalid identifier

**Request**

- Method: `DELETE`
- URL: `{{baseUrl}}/api/v1/offerings/{{offeringId}}/enrollments/invalid%20learner`

**Expected**

- Status: `400 Bad Request`
- `code`: `INVALID_REQUEST`
- no state change

## 18. Locking evidence for panel demonstration

Use Postman Collection Runner or an external terminal to send one learner
request at the same time. Keep capacity at `2` and demonstrate that at most
two requests can result in `ENROLLED`.

You can restart the application and use 12 unique body values with a Collection
Runner. Repeating the same learner in several concurrent requests should show
only one enrollment record. The database uses a pessimistic offering-row lock
inside the JPA transaction, so capacity and promotion mutations are serialized
for concurrent requests in this single database instance.

**Expected**

- exactly `capacity` enrolled records;
- the remaining eligible requests remain waitlisted once;
- duplicate IDs create only one record;
- no enrolled count exceeds `2`.
