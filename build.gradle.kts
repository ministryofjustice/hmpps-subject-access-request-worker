plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "8.0.0"
  kotlin("plugin.spring") version "2.1.20"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencyCheck {
  suppressionFiles.add("hmpps-sar-worker-suppressions.xml")
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.json:json:20250107")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.1")
  implementation("com.itextpdf:itext7-core:9.1.0")
  implementation("com.itextpdf:html2pdf:6.1.0")
  implementation("org.springframework.boot:spring-boot-starter-mustache")
  implementation("com.github.spullara.mustache.java:compiler:0.9.14")
  implementation("com.github.jknack:handlebars:4.4.0")
  implementation("org.apache.commons:commons-lang3:3.17.0")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.3")
  implementation("io.sentry:sentry-spring-boot-starter-jakarta:8.6.0")
  implementation("io.sentry:sentry-logback:8.6.0")
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.4.2")
  implementation("commons-io:commons-io:2.18.0")
  implementation("aws.sdk.kotlin:s3:1.4.38")

  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("com.h2database:h2:2.3.232")
  runtimeOnly("org.postgresql:postgresql:42.7.5")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.4.2")
  testImplementation("org.wiremock:wiremock-standalone:3.12.1")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("com.google.code.gson:gson:2.12.1")
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}
