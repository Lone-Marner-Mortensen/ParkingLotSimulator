# Parking Lot Simulator

A parking lot simulation project built with Spring Boot. </br>
It gives the number of cars who can enter the parking lot and which spots are available. </br> </br>
**A lot of classes/implementations are abstracted away.** </br>
**We pretend that there are a sensor system, payment system and a parking guard notification system
that we can connect to.** </br>
It's not a project that is meant to go live.</br>

The project follows a relaxed hexagonal architecture.</br>
Everything depends on the domain, which exposes interfaces (also called ports) to the other components. 

## Overview

The simulator is designed around a `ParkingController` that listens to sensor events such as:

| Event | Meaning                                                                                                |
| --- |--------------------------------------------------------------------------------------------------------|
| vehicle entering | The vehicle is actively being checked to determine whether it is allowed to enter the parking lot. |
| parking spot occupied | A vehicle has occupied a parking spot.                                                                 |
| vehicle leaving | The vehicle is leaving the parking lot, but the spot is not yet released.                              |
| parking spot released | A vehicle has left a parking spot, making it available.                                                |
| vehicle overstaying | A vehicle has exceeded the allowed parking time.                                                       |

It evaluates the events against external checks (plate reading, size estimation, payment status) and decides whether to allow entry or notify the parking guard.
#### Only one entering lane
There is only one waiting lane and only one parking payment machine. That means, 
that vehicle-entering events can not happen concurrently.
#### Events happens in a certain order
Event are happening in the following order: Vehicle entering -> parking spot occupied -> vehicle leaving -> parking spot released.
Sometimes people change their mind about leaving the parking-spot, so 
vehicle leaving -> parking spot occupied and parking spot released -> vehicle leaving is also allowed. And of course parking spot occupied -> vehicle overstaying is also allowed.

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
- Docker Desktop or Docker Engine (must be up when running the app)
- Gradle wrapper (bundled with the repo)

## Getting started

### Run the app
```bash
./gradlew start
```

This task starts the PostgreSQL container via Docker Compose and then launches the Spring Boot application
and runs the DemoSensorEventRunner. After running it you are expected to see: 
```
Taken spots: [A22, A23, A24, A25]
Vehicle in transition: 6
```
in the log. DemoSensorEventRunner runs a predefined event list. 
If you want to run the application with another event list, you must change the DemoSensorEventRunner manually.
A user interface for the DemoSensorEventRunner is on the way.


### Stop the app

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
