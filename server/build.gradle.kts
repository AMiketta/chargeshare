plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("app.chargeshare.server.ApplicationKt")
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.json)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.logback.classic)
    implementation(libs.bcrypt)
    implementation(libs.stripe.java)
    implementation(libs.ktor.server.websockets)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.embedded.postgres)
    testImplementation(libs.ktor.client.websockets)
}

tasks.test {
    useJUnitPlatform()
}

// Lokaler Dev-Server OHNE Docker: startet ein eingebettetes PostgreSQL (Daten in server/.local-pg) und den Server
// mit Dev-Seed (Beispieldaten in Bremen) und Fake-Payment. `./gradlew :server:runLocal`
tasks.register<JavaExec>("runLocal") {
    group = "application"
    description = "Startet Server + eingebettetes PostgreSQL + Dev-Seed (ohne Docker)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("app.chargeshare.server.LocalDevMainKt")
    workingDir = projectDir
}
