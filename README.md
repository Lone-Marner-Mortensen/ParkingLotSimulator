# Parking Lot Simulator

A parking lot simulation project built with Spring Boot. </br>
It gives the number of cars who can enter the parking lot and what spots are available. </br> </br>
A lot of classes/implementation are abstracted away. </br>
We pretend that there are a sensor system, payment system and a parking guard notification system 
that we can connect to. </br>
It's not a project that is meant to go live.</br>

## Overview

The simulator is designed around a `ParkingController` that listens for sensor events such as:

| Event | Meaning                                                                                                |
| --- |--------------------------------------------------------------------------------------------------------|
| vehicle entering | The vehicle is actively being checked to determine whether it is allowed to enter the parking lot. |
| vehicle leaving | The vehicle is leaving the parking lot, but the spot is not yet released.                              |
| parking spot occupied | A vehicle has occupied a parking spot.                                                                 |
| parking spot released | A vehicle has left a parking spot, making it available.                                                |
| vehicle overstaying | A vehicle has exceeded the allowed parking time.                                                       |

It evaluates the events against external checks (plate reading, size estimation, payment status) and decides whether to allow entry or notify the parking guard.
There is only one waiting lane and only one parking payment machine. **That means, 
that vehicle-entering events can not happen concurrently.**

## Tech stack

- Kotlin
- Spring Boot 4.1
- PostgreSQL
- Flyway
- SQLDelight
- Docker Compose
- Gradle

## Prerequisites

- JDK 17+
- Docker Desktop or Docker Engine
- Gradle wrapper (bundled with the repo)

## Getting started

### Run the app

```bash
./gradlew bootRun
```

### Start the local database and app together

```bash
./gradlew start
```

This task starts the PostgreSQL container via Docker Compose and then launches the Spring Boot application.

### Stop the app and database

```bash
./gradlew stop
```

## Useful commands

```bash
./gradlew test
./gradlew bootRun
./gradlew start
./gradlew stop
```
