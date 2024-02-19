plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.13.0"
  kotlin("plugin.spring") version "1.9.22"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")
  testImplementation("org.wiremock:wiremock-standalone:3.3.1")
  testImplementation("io.kotest:kotest-assertions-json-jvm:5.8.0")
  testImplementation("io.kotest:kotest-runner-junit5-jvm:5.8.0")
  testImplementation("io.kotest:kotest-assertions-core-jvm:5.8.0")
  testImplementation("io.kotest.extensions:kotest-extensions-spring:1.1.3")
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "21"
    }
  }
}
