# Parking Lot Simulator

A parking lot simulation project built with kotlin and Spring Boot. </br>
It gives the number of vehicles who can enter the parking lot and which spots are available. </br> </br>
**A lot of classes/implementations are abstracted away.** </br>
**We pretend that there are a sensor system, payment system and a parking guard notification system
that we can connect to.** </br>

The project follows hexagonal architecture, but not in a strict sense.</br>
Everything depends on the domain, which exposes interfaces to the other components. 

## Overview

The simulator is designed around a `ParkingLotManager` that listens to sensor events such as:

| Event | Meaning                                                                                            |
| --- |----------------------------------------------------------------------------------------------------|
| vehicle entering | The vehicle is actively being checked to determine whether it is allowed to enter the parking lot. |
| parking spot occupied | A vehicle has just occupied a parking spot.                                                        |
| vehicle leaving | The vehicle is leaving the parking lot, but the spot is not yet released.                          |
| parking spot released | A vehicle has just left a parking spot, making it available.                                       |
| vehicle overstaying | A vehicle has exceeded the allowed parking time.                                                   |

It evaluates the events against external checks (plate reading, size estimation, payment status) and decides whether to allow entry or notify the parking guard.
#### Only one entering lane
There is only one waiting lane and only one parking payment machine. That means, 
that vehicle-entering events can not happen concurrently.
#### Events happens in a certain order
Event are happening in the following order: Vehicle entering -> parking spot occupied -> vehicle leaving -> parking spot released.
Sometimes people change their mind about leaving the parking-spot, so 
vehicle leaving -> parking spot occupied and parking spot released -> vehicle leaving is also allowed. 
And of course parking spot occupied -> vehicle overstaying is also allowed.
<ins>In short, all event-sequences that make sense are allowed</ins>. 
Vehicle leaving and vehicle overstaying may occur multiple times in a row. 
Such repetitions provide no additional benefit, but they do not cause any harm either.

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
Run the application with:
```bash
./gradlew start
```
This starts the PostgreSQL container via Docker Compose and then launches the application. 
You can also run the application directly from IntelliJ. </br> 

If the input is not valid, you will see an error-message and the application stops.

#### Running on a predefined event list

By default, the application runs the DemoParkingLotSimulator, which uses a predefined list of events.
After running the application, you are expected to see:

```
Taken spots: [A22, A23, A24, A25, B1, B2, B3]
Vehicles in transit: 6
```
as one of the last statements in the log. Because allowing vehicles to enter the parking lot is based on a random number generator,
the number of vehicles in transit might be lower in rare cases.

#### Running on a non-predefined event list
To run the application with a different event list, you currently need to modify DemoParkingLotSimulator manually. </br> 

<ins>A user interface for running ParkingLotSimulatorApplication with a non-predefined event list or 
randomized events has not been implemented yet.</ins></br> Also, a vehicle-entering event can only simulate a car
coming in with an unknown license-plate (in real life you don't know the license-plate until it's scanned); to simulate a car coming in with a certain license-plate, we have to add the car to the vehicleTransitRepository, see DemoParkingLotSimulator. 
Adding vehicle-entering event with a license-plate is also for next iteration.</br> 

Since the validity of an event can depend on previous events, assessing the validity of a single event may 
require examining the entire event list. Therefore, early validation—validating all events before the first event is 
fully processed—would slow down the application and has not been implemented.

### Stop the app
The app exits automatically once it has finished processing the event list. To stop and remove the Postgres container, run:

```bash
./gradlew stop-db
```
