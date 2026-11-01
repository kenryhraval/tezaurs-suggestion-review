# Integration tests

Requires Java 21+ and Docker. From the repository root:

```bash
./backend/mvnw -f backend/pom.xml -DskipTests package
./backend/mvnw -f integration-tests/pom.xml test
```

The first command builds the backend; repeat it after backend changes.
Cucumber starts a temporary PostgreSQL container and backend on a free port,
resetting synthetic fixtures before each scenario.
The legacy schema fixture contains the mapped tables from the Tēzaurs dump;
the actual Flyway migration creates `review`.

Scenarios exercise API reads and edits, and check that `dict` and `incubator`
remain unchanged. The test backend and database are stopped afterward.

Report: `integration-tests/target/cucumber.html`. Backend log: `integration-tests/target/backend.log`.
