# Transporter Assignment on Lanes

A Spring Boot service that assigns transporters to freight lanes so that **every lane is covered at the lowest
total cost**, using **at most `maxTransporters`** different transporters.

* **Live URL:** `<add your Render URL here>`
* **Swagger UI:** `<live URL>/swagger-ui/index.html`
* **Demo video (Loom):** `<add your Loom link here>`

## API

### `POST /api/v1/transporters/input`
Stores the lanes and transporter quotes. Each call **replaces** the previously stored data set.

```json
{
  "lanes": [{"id": 1, "origin": "Mumbai", "destination": "Delhi"}],
  "transporters": [
    {"id": 1, "name": "Transporter T1", "laneQuotes": [{"laneId": 1, "quote": 5000}]}
  ]
}
```
Response `200`: `{"status": "success", "message": "Input data saved successfully."}`

### `POST /api/v1/transporters/assignment`
```json
{ "maxTransporters": 3 }
```
Response `200`:
```json
{
  "status": "success",
  "totalCost": 60139,
  "assignments": [{"laneId": 1, "transporterId": 5}, "..."],
  "selectedTransporters": [1, 4, 5]
}
```

### Error responses
All errors share one shape: `{"status": "error", "message": "...", "details": ["..."]}` (`details` only when there is more to say).

| Code | When |
|------|------|
| 400  | Malformed JSON, missing/invalid fields, duplicate lane or transporter ids, a quote for an unknown lane, `maxTransporters` < 1 |
| 409  | `/assignment` called before any input was submitted |
| 422  | The lanes cannot be covered: a lane has no quotes, or `maxTransporters` is too small (the message says how many are needed) |
| 503  | The optimiser hit its time limit without finding any valid assignment (very large inputs only) |

## How the objectives are interpreted

The problem statement lists three objectives; this is how they are applied:

1. **Full lane coverage (hard rule).** Every lane is assigned to exactly one transporter, and only to a transporter who quoted for that lane.
2. **Cost minimisation.** Among all valid assignments that use at most `maxTransporters` transporters, the cheapest total is returned.
3. **Maximise transporter usage.** Among assignments with the *same lowest cost*, the one that uses the most transporters (up to the limit) is returned. `selectedTransporters` lists the transporters that actually received at least one lane.

Other assumptions: a transporter can serve several lanes; quotes are positive whole numbers; if two transporters quote the same price for a lane, the lower transporter id wins.

## Algorithm

For a fixed set of chosen transporters the best assignment is trivial: each lane goes to its cheapest chosen transporter. So only the *set* of transporters has to be searched. The solver (`solver/AssignmentSolver.java`, plain Java without Spring) does a depth-first **branch and bound** over that set:

* A lower bound (for every lane, the cheapest quote among the chosen and still-undecided transporters) prunes branches that cannot beat the best solution found so far.
* A transporter is only added if it wins at least one lane.
* The general problem is NP-hard (it is a set-cover / facility-location variant), so there is an 8 second time budget (`assignment.solver.time-budget-ms`). If it runs out, the best solution found so far is returned with `"optimal": false`.

The solver is cross-checked against exhaustive search on 2,000 random instances in `AssignmentSolverTest`. A 30-transporter, 40-lane, `maxTransporters = 8` instance solves to proven optimality in about one second.

### Results for the sample data

The example responses in the problem statement are illustrative: for example, the Test Case 1 response shows a total of 75000 and assigns lane 3 to transporter 2, who is not in its own `selectedTransporters`. Exhaustive search over the Test Case 1 data gives these true optima:

| maxTransporters | totalCost | selectedTransporters |
|-----------------|-----------|----------------------|
| 1 | 96940 | 4 |
| 2 | 62938 | 1, 4 |
| **3** | **60139** | **1, 4, 5** |
| 4 | 59896 | 1, 3, 4, 5 |

Test Case 2 (transporters quote only some lanes): `maxTransporters = 3` gives 7500 using transporters 1, 2, 3; `4` gives 7450 (transporter 4 takes lane 4); `2` is impossible, because lanes 2, 3 and 5 each have only one possible transporter.

## Run locally

Requires Java 21. Copy `.mvn`, `mvnw` and `mvnw.cmd` from any Spring Boot project if they are missing, then:

```bash
./mvnw spring-boot:run
```
Open http://localhost:8080/swagger-ui/index.html. By default data lives in an in-memory H2 database (it resets on restart).

### Use PostgreSQL instead
Set these environment variables; no code change is needed:
```
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<database>
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<password>
```

## Tests

```bash
./mvnw test
```
| Test class | What it covers |
|------------|----------------|
| `AssignmentSolverTest` | Sample test cases, limits, infeasible cases, tie-breaking, randomised comparison with exhaustive search |
| `TransporterAssignmentServiceTest` | Input validation rules, replacing data, error messages (Mockito) |
| `TransporterControllerTest` | HTTP status codes, validation messages, JSON shape (`@WebMvcTest`) |
| `TransporterAssignmentIntegrationTest` | Full flow with the sample data against an in-memory database |

Coverage report: `target/site/jacoco/index.html`.

## Deploy (Render)

1. Push the repository to GitHub.
2. In Render: **New > Web Service**, pick the repo, runtime **Docker** (the `Dockerfile` is included).
3. (Optional) add the PostgreSQL environment variables above, otherwise the in-memory database is used.
4. After the deploy, open `<service URL>/swagger-ui/index.html`.

## Project layout

```
controller/   REST endpoints and Swagger annotations
service/      validation, persistence, calls the solver
solver/       the optimisation algorithm (no Spring)
entity/ repository/   JPA storage (lanes, transporters, lane_quotes)
dto/ exception/       request/response models and error handling
```
