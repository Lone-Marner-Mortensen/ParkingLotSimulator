# Parking Lot Simulator

A parking lot simulation project built with Spring Boot. </br>
It gives the number of vehicles who can enter the parking lot and which spots are available. </br> </br>
**A lot of classes/implementations are abstracted away.** </br>
**We pretend that there are a sensor system, payment system and a parking guard notification system
that we can connect to.** </br>

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
- Docker Desktop or Docker Engine (must be up to start the app)
- Gradle wrapper (bundled with the repo)

## Getting started

### Run the app
```bash
./gradlew start
```

Starts the PostgreSQL container via Docker Compose and then launches the application
and runs the DemoParkingLotSimulator. You can also run the App via Intellij. After running it you are expected to see: 
```
Taken spots: [A22, A23, A24, A25, B1, B2, B3]
Vehicle in transition: 6
```
as one of the last statements in the log. DemoParkingLotSimulator runs a predefined event list. 
If you want to run the application with another event list, you must change the DemoParkingLotSimulator manually.
If input is not valid, the application will throw a descriptive exception and stop.

#### User interface
A user interface for running ParkingLotSimulatorApplication on a non predefined event list or with randomized events is not implemented at this point.
Since the validity of an event depends on previous events, you might have to look at the entire list to assess the validity of a single event.
Therefore, early validation (i.e., validating all events before the first event is fully processed) would slow down the application and is not implemented either.

### Stop the app
```bash
./gradlew stop
```
Stops docker and removes the PostgreSQL container and stops the application.
