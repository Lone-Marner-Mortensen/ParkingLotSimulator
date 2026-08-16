# Postgres + Flyway + SQLDelight startup infrastructure

## Goal

Running the app locally (via `./gradlew bootRun` or the IDE run
configuration) should:

1. Start a Postgres container automatically (no manual `docker run`).
2. Apply Flyway migrations against it automatically on startup.
3. Prove that SQLDelight-generated queries can run against that same
   database.

The `car` table (below) is the first real domain table, used here to
prove the pipeline end-to-end. Further domain tables (parking spots,
tickets, etc.) remain a separate follow-up task.

## Components

### 1. Docker Compose-managed Postgres

- Add `compose.yaml` at the project root, declaring a single
  `postgres:16` service with a named volume (`pgdata`) so data
  survives container restarts across dev sessions.
- Add dependency `org.springframework.boot:spring-boot-docker-compose`.
- Spring Boot's Docker Compose support auto-detects `compose.yaml` at
  startup, runs `docker compose up` for the declared service, and
  registers a `DataSource` bean via its Postgres **service
  connection** — no `spring.datasource.*` properties needed in
  `application.properties`.
- On JVM shutdown, Spring Boot stops (not removes) the container by
  default, so the named volume persists between runs. `docker compose
  down -v` remains the manual way to fully reset.

### 2. Flyway migrations

- `spring-boot-starter-flyway` is already a dependency.
- Flyway autoconfiguration activates once the Docker-Compose-backed
  `DataSource` bean exists, and runs before the application context
  finishes starting.
- Add `src/main/resources/db/migration/V1__init.sql`:
  ```sql
  CREATE TABLE IF NOT EXISTS car (
      plate TEXT NOT NULL PRIMARY KEY,
      vehicleType TEXT NOT NULL,
      entryTime INTEGER NOT NULL,
      exitTime INTEGER,
      spots TEXT NOT NULL,
      status TEXT NOT NULL
  );
  ```
  (The SQLDelight-only `AS org.example.VehicleType` type annotation
  lives in the `.sq` file, not the Flyway SQL — Postgres itself just
  sees a `TEXT` column.)

### 3. SQLDelight

- Add the SQLDelight Gradle plugin (`app.cash.sqldelight`, version
  `2.3.2`) plus runtime, `postgresql-dialect`, and `jdbc-driver`
  dependencies.
- Configure the plugin to generate a `Database` interface (package
  `org.example.parkinglotsystemkotlin.db`) from `.sq` files under
  `src/main/sqldelight/org/example/parkinglotsystemkotlin/db/`.
- Add a Kotlin enum `VehicleType` (package `org.example`, values `CAR,
  MOTORCYCLE, TRUCK, VAN`) — SQLDelight generates a TEXT column
  adapter for it automatically via the `AS org.example.VehicleType`
  annotation.
- Add `Car.sq` under
  `src/main/sqldelight/org/example/parkinglotsystemkotlin/db/`:
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
- `status` is a plain `TEXT` column (no adapter) holding `'PARKED'` or
  `'EXITED'`. `spots` is a plain `TEXT` column holding a single spot
  id (e.g. `"A1"`) — one car occupies one spot in this model.
- SQLDelight's `CREATE TABLE` statement in `Car.sq` mirrors the Flyway
  migration by hand — Flyway is the source of truth for the real
  database schema (it's what actually runs against Postgres);
  SQLDelight only needs matching `.sq`-side schema text to generate
  correctly-typed query code. (SQLDelight is used purely as a typed
  query layer here, not as its own migration/schema-authority tool.)

### 4. Wiring SQLDelight to the Spring-managed DataSource

- A `@Configuration` class (`DatabaseConfig`) exposes a `Database`
  bean:
  ```kotlin
  @Bean
  fun sqlDelightDatabase(dataSource: DataSource): Database {
      val driver = dataSource.asJdbcDriver()
      return Database(driver)
  }
  ```
- This reuses the same Hikari-pooled `DataSource` that Flyway used —
  one connection pool, no duplicated JDBC config.

### 5. End-to-end startup proof

- A `CommandLineRunner` bean:
  1. Inserts a sample car (e.g. plate `"AB12345"`, `VehicleType.CAR`,
     current epoch millis as `entryTime`, spot `"A1"`) via
     `carQueries.insert(...)`, guarded so a re-run doesn't fail on the
     primary key (e.g. check `findByPlate` first, or catch/ignore a
     duplicate-key error).
  2. Calls `carQueries.findByPlate("AB12345").executeAsOneOrNull()`
     and `carQueries.listParked().executeAsList()`, logging both
     results.
- Successful boot log output showing the inserted car and the parked
  list confirms: container started → Flyway migrated → SQLDelight
  insert/query executed, all via `bootRun`.

## Testing / verification

1. `./gradlew bootRun` — confirm log output shows:
   - Docker Compose starting the `postgres` service
   - Flyway applying `V1__init.sql`
   - The `CommandLineRunner` logging the inserted car and the parked
     list containing it
2. Stop the app, run `docker ps` — confirm the container is no longer
   running (Spring Boot stopped it), but `docker volume ls` still
   shows `pgdata`.
3. Run `bootRun` again — confirm Flyway reports the migration as
   already applied (no re-run), and the sample car is found via
   `findByPlate` without a duplicate-insert error (proves the volume
   persisted data).

## Out of scope

- Further parking-lot domain tables (`parking_spot`, `ticket`, etc.)
  and their SQLDelight queries — future task.
- Multi-spot cars (`spots` holds a single spot id for now).
- Production database configuration (this Docker Compose setup is
  dev/local-only; Spring Boot skips it automatically for tests and
  packaged jars unless explicitly configured otherwise).
- CI database provisioning.
