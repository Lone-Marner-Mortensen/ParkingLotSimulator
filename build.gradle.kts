plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("app.cash.sqldelight") version "2.3.2"
}

group = "org.example"
version = "0.0.1-SNAPSHOT"
description = "ParkingLotSimulator"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("app.cash.sqldelight:runtime:2.3.2")
    implementation("app.cash.sqldelight:jdbc-driver:2.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("io.arrow-kt:arrow-core:2.1.2")
    implementation("io.arrow-kt:arrow-fx-coroutines:2.1.2")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("org.example.parkinglotsimulator.db")
            dialect("app.cash.sqldelight:postgresql-dialect:2.3.2")
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val composeUp = tasks.register<Exec>("composeUp") {
    commandLine("docker", "compose", "up", "-d")
}

val composeDown = tasks.register<Exec>("composeDown") {
    commandLine("docker", "compose", "down")
}

val killApp = tasks.register<Exec>("killApp") {
    commandLine("pkill", "-f", "org.example.parkinglotsimulator.ParkingLotSimulatorApplicationKt")
    isIgnoreExitValue = true
}

tasks.named("bootRun") {
    mustRunAfter(composeUp)
}

tasks.register("start") {
    group = "application"
    description = "Starts Postgres and runs the application (Ctrl+C to stop the app; Postgres keeps running)."
    dependsOn(composeUp, "bootRun")
}

tasks.register("stop") {
    group = "application"
    description = "Stops the application (if running) and stops/removes the Postgres container."
    dependsOn(killApp, composeDown)
}

tasks.named("composeDown") {
    mustRunAfter(killApp)
}
