# Postgres + Flyway + SQLDelight Startup Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `./gradlew bootRun` automatically start a Postgres container, apply Flyway migrations creating a `car` table, and prove SQLDelight can insert/query rows against it.

**Architecture:** Spring Boot's Docker Compose support auto-starts a `postgres:16` container declared in `compose.yaml` and registers a `DataSource` bean from it. Flyway (already on the classpath) runs `V1__init.sql` against that `DataSource` on startup. A `DatabaseConfig` bean wraps the same `DataSource` with SQLDelight's JDBC driver to build a generated `Database` (with an `EnumColumnAdapter` for `VehicleType`). A `CommandLineRunner` inserts a sample car and queries it back, proving the full chain works.

**Tech Stack:** Kotlin 2.3.21, Spring Boot 4.1.0, PostgreSQL 16 (Docker), Flyway (via `spring-boot-starter-flyway`), SQLDelight 2.3.2 (`postgresql-dialect`, `jdbc-driver`), Gradle Kotlin DSL.

**Spec:** `docs/superpowers/specs/2026-08-16-postgres-flyway-sqldelight-infra-design.md`

## Global Constraints

- SQLDelight version: `2.3.2` (Gradle plugin, `runtime`, `postgresql-dialect`, `jdbc-driver` — all pinned to this version).
- Postgres image: `postgres:16`.
- Docker Compose dependency: `org.springframework.boot:spring-boot-docker-compose`, added as `developmentOnly` (Spring Boot skips Docker Compose in tests by default via `spring.docker.compose.skip.in-tests=true` — this plan does not change that default, so tests never need Docker running).
- SQLDelight generated package: `org.example.parkinglotsystemkotlin.db`; `.sq` files live under `src/main/sqldelight/org/example/parkinglotsystemkotlin/db/`.
- `VehicleType` enum lives in package `org.example` (matches the `AS org.example.VehicleType` annotation in the `car` table spec) with values `CAR, MOTORCYCLE, TRUCK, VAN`.
- Enum columns require an explicit `EnumColumnAdapter()` passed into the generated `Car.Adapter` — SQLDelight does not wire this automatically.
- Flyway's `V1__init.sql` is the schema source of truth for Postgres; the `.sq` file's `CREATE TABLE` is hand-mirrored for SQLDelight codegen only and never executed as a migration.
- Named Docker volume `pgdata` persists Postgres data across `bootRun` stop/start cycles.

---

### Task 1: Docker Compose Postgres + Flyway migration

**Files:**
- Create: `compose.yaml`
- Modify: `build.gradle.kts` (add `spring-boot-docker-compose` dependency)
- Create: `src/main/resources/db/migration/V1__init.sql`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: a running Postgres container reachable via Spring Boot's auto-configured `DataSource` bean, and a `car` table matching the spec's DDL. Task 2 depends on this `DataSource` bean existing and the `car` table existing in the DB.

- [ ] **Step 1: Create `compose.yaml` at the project root**

```yaml
services:
  postgres:
    image: 'postgres:16'
    environment:
      - 'POSTGRES_DB=parkinglot'
      - 'POSTGRES_USER=parkinglot'
      - 'POSTGRES_PASSWORD=parkinglot'
    ports:
      - '5432'
    volumes:
      - 'pgdata:/var/lib/postgresql/data'

volumes:
  pgdata:
```

Note: `ports: ['5432']` (no host-port pinning) lets Docker assign a random host port, avoiding conflicts with any other local Postgres. Spring Boot's service connection reads the actual mapped port automatically — no manual `spring.datasource.*` config needed.

- [ ] **Step 2: Add the Docker Compose dependency in `build.gradle.kts`**

Edit `build.gradle.kts`, in the `dependencies { ... }` block, add this line right after the existing `developmentOnly("org.springframework.boot:spring-boot-devtools")` line:

```kotlin
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
```

- [ ] **Step 3: Create the Flyway migration**

Create `src/main/resources/db/migration/V1__init.sql`:

```sql
CREATE TABLE IF NOT EXISTS car (
    plate TEXT NOT NULL PRIMARY KEY,
    vehicleType TEXT NOT NULL,
    entryTime BIGINT NOT NULL,
    exitTime BIGINT,
    spots TEXT NOT NULL,
    status TEXT NOT NULL
);
```

Note: uses `BIGINT` (Postgres 64-bit integer) for `entryTime`/`exitTime` since they hold epoch milliseconds, which overflow a 32-bit `INTEGER`. SQLDelight's `.sq` file in Task 2 declares these same columns as `INTEGER`, which SQLDelight maps to Kotlin `Long` for the Postgres dialect regardless of the SQL integer keyword used — the Flyway DDL is what Postgres actually enforces, so `BIGINT` here is what matters for not overflowing.

- [ ] **Step 4: Verify the container starts and the migration applies**

Run: `./gradlew bootRun` (let it run for ~15 seconds, then stop with Ctrl+C)
Expected in the console output:
- A line showing Docker Compose starting the `postgres` service (e.g. `Container ... Started` or `Network ... Created`)
- A Flyway log line like `Migrating schema "public" to version "1 - init"`
- The app reaches `Started ParkingLotSystemKotlinApplicationTests` / `Started ParkingLotSystemKotlinApplication` without errors

- [ ] **Step 5: Commit**

```bash
git add compose.yaml build.gradle.kts src/main/resources/db/migration/V1__init.sql
git commit -m "Add Docker Compose Postgres and Flyway car table migration"
```

---

### Task 2: SQLDelight setup, VehicleType enum, and Car.sq

**Files:**
- Modify: `build.gradle.kts` (add SQLDelight plugin + dependencies + `sqldelight { }` config block)
- Create: `src/main/kotlin/org/example/VehicleType.kt`
- Create: `src/main/sqldelight/org/example/parkinglotsystemkotlin/db/Car.sq`

**Interfaces:**
- Consumes: the `car` table shape from Task 1 (must match exactly, since this `.sq` file's `CREATE TABLE` is hand-mirrored against it).
- Produces: generated `Database` interface (package `org.example.parkinglotsystemkotlin.db`) with `carQueries` exposing `insert(plate: String, vehicleType: VehicleType, entryTime: Long, spots: String)`, `findByPlate(plate: String): Query<Car>`, `markExited(exitTime: Long, plate: String)`, `listParked(): Query<Car>`. Generated `Car.Adapter` class requiring a `vehicleTypeAdapter: ColumnAdapter<VehicleType, String>`. Task 3 depends on these exact names/types.

- [ ] **Step 1: Add the SQLDelight plugin to the `plugins { }` block in `build.gradle.kts`**

```kotlin
    id("app.cash.sqldelight") version "2.3.2"
```

- [ ] **Step 2: Add SQLDelight dependencies to the `dependencies { }` block in `build.gradle.kts`**

```kotlin
    implementation("app.cash.sqldelight:runtime:2.3.2")
    implementation("app.cash.sqldelight:jdbc-driver:2.3.2")
```

- [ ] **Step 3: Add the `sqldelight { }` configuration block**

Add this block after the `dependencies { }` block in `build.gradle.kts`:

```kotlin
sqldelight {
    databases {
        create("Database") {
            packageName.set("org.example.parkinglotsystemkotlin.db")
            dialect("app.cash.sqldelight:postgresql-dialect:2.3.2")
        }
    }
}
```

- [ ] **Step 4: Create the `VehicleType` enum**

Create `src/main/kotlin/org/example/VehicleType.kt`:

```kotlin
package org.example

enum class VehicleType {
    CAR,
    MOTORCYCLE,
    TRUCK,
    VAN,
}
```

- [ ] **Step 5: Create `Car.sq`**

Create `src/main/sqldelight/org/example/parkinglotsystemkotlin/db/Car.sq`:

```sql
CREATE TABLE IF NOT EXISTS car (
    plate TEXT NOT NULL PRIMARY KEY,
    vehicleType TEXT AS org.example.VehicleType NOT NULL,
    entryTime INTEGER NOT NULL,
    exitTime INTEGER,
    spots TEXT NOT NULL,
    status TEXT NOT NULL
);

insert:
INSERT INTO car (plate, vehicleType, entryTime, exitTime, spots, status)
VALUES (?, ?, ?, NULL, ?, 'PARKED');

findByPlate:
SELECT * FROM car WHERE plate = ?;

markExited:
UPDATE car SET exitTime = ?, status = 'EXITED' WHERE plate = ?;

listParked:
SELECT * FROM car WHERE status = 'PARKED';
```

- [ ] **Step 6: Verify codegen compiles**

Run: `./gradlew generateMainDatabaseInterface compileKotlin`
Expected: `BUILD SUCCESSFUL`. This confirms SQLDelight generated `Database`, `Car`, `Car.Adapter`, and `CarQueries` classes without SQL/type errors.

- [ ] **Step 7: Commit**

```bash
git add build.gradle.kts src/main/kotlin/org/example/VehicleType.kt src/main/sqldelight/org/example/parkinglotsystemkotlin/db/Car.sq
git commit -m "Add SQLDelight plugin, VehicleType enum, and Car.sq queries"
```

---

### Task 3: Wire SQLDelight Database bean and startup proof runner

**Files:**
- Create: `src/main/kotlin/org/example/parkinglotsystemkotlin/db/DatabaseConfig.kt`
- Create: `src/main/kotlin/org/example/parkinglotsystemkotlin/CarStartupRunner.kt`
- Test: `src/test/kotlin/org/example/parkinglotsystemkotlin/ParkingLotSystemKotlinApplicationTests.kt` (verify existing `contextLoads` still passes with the new beans present)

**Interfaces:**
- Consumes: `Database`, `Car`, `Car.Adapter`, `CarQueries` from Task 2's generated code; `VehicleType` from Task 2; the Spring-managed `DataSource` bean from Task 1's Docker Compose setup.
- Produces: a `Database` Spring bean (name `sqlDelightDatabase`) available for future domain code to inject.

- [ ] **Step 1: Create `DatabaseConfig`**

Create `src/main/kotlin/org/example/parkinglotsystemkotlin/db/DatabaseConfig.kt`:

```kotlin
package org.example.parkinglotsystemkotlin.db

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class DatabaseConfig {

    @Bean
    fun sqlDelightDatabase(dataSource: DataSource): Database {
        val driver = dataSource.asJdbcDriver()
        return Database(
            driver = driver,
            carAdapter = Car.Adapter(
                vehicleTypeAdapter = EnumColumnAdapter(),
            ),
        )
    }
}
```

- [ ] **Step 2: Create the startup proof runner**

Create `src/main/kotlin/org/example/parkinglotsystemkotlin/CarStartupRunner.kt`:

```kotlin
package org.example.parkinglotsystemkotlin

import org.example.VehicleType
import org.example.parkinglotsystemkotlin.db.Database
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class CarStartupRunner(private val database: Database) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(CarStartupRunner::class.java)

    override fun run(vararg args: String?) {
        val plate = "AB12345"
        val queries = database.carQueries

        if (queries.findByPlate(plate).executeAsOneOrNull() == null) {
            queries.insert(
                plate = plate,
                vehicleType = VehicleType.CAR,
                entryTime = System.currentTimeMillis(),
                spots = "A1",
            )
        }

        val found = queries.findByPlate(plate).executeAsOneOrNull()
        val parked = queries.listParked().executeAsList()

        log.info("SQLDelight startup check — found car: {}", found)
        log.info("SQLDelight startup check — parked cars: {}", parked)
    }
}
```

- [ ] **Step 3: Run the full startup and verify the pipeline end-to-end**

Run: `./gradlew bootRun` (let it run for ~15 seconds after startup, then stop with Ctrl+C)
Expected in the console output:
- Docker Compose starting the `postgres` service
- Flyway migrating `V1__init.sql`
- A log line `SQLDelight startup check — found car: Car(plate=AB12345, ...)`
- A log line `SQLDelight startup check — parked cars: [Car(plate=AB12345, ...)]`

- [ ] **Step 4: Verify re-running doesn't duplicate or error**

Run: `./gradlew bootRun` again (second run, let it run ~15 seconds, then stop)
Expected:
- Flyway log line indicating schema already at version 1 (no re-migration, e.g. no `Migrating schema` line this time — only Flyway's "Current version of schema" info line)
- The same `found car` / `parked cars` log lines appear, with no duplicate-key exception
- This confirms the `pgdata` volume persisted data across the container stop/start

- [ ] **Step 5: Run the existing test suite to confirm nothing broke**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`, `ParkingLotSystemKotlinApplicationTests.contextLoads` passes (Spring context loads cleanly with the new `DatabaseConfig` and `CarStartupRunner` beans present; Docker Compose is skipped in tests by default so this test does not require Docker).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/example/parkinglotsystemkotlin/db/DatabaseConfig.kt src/main/kotlin/org/example/parkinglotsystemkotlin/CarStartupRunner.kt
git commit -m "Wire SQLDelight Database bean and add startup proof runner"
```

---

### Task 4: Manual container lifecycle verification

**Files:** none (verification-only task, no code changes)

**Interfaces:**
- Consumes: the full running app from Task 3.
- Produces: nothing (confirms the spec's "Testing / verification" section end-to-end).

- [ ] **Step 1: Start the app and confirm the container is running**

Run: `./gradlew bootRun` in one terminal, then in another terminal run `docker ps`
Expected: a `postgres:16` container listed as `Up`.

- [ ] **Step 2: Stop the app and confirm the container stops but the volume remains**

Stop `bootRun` with Ctrl+C, then run `docker ps -a` and `docker volume ls`
Expected: the `postgres:16` container shows as `Exited` (not removed), and a volume named like `<project>_pgdata` is still listed.

- [ ] **Step 3: Full teardown (optional manual step, not run as part of this task)**

Document in this task's completion notes that `docker compose down -v` removes the container and the `pgdata` volume for a full reset. Do not run this as part of verification — it would erase the row inserted in Task 3, and re-verifying persistence would require redoing Task 3 Step 4.

No commit — this task produces no file changes.
