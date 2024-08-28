plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.15.6"
  kotlin("plugin.spring") version "1.9.23"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.json:json:20231013")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")
  implementation("com.itextpdf:itext7-core:7.2.6")
  implementation("com.itextpdf:html2pdf:5.0.4")
  implementation("org.springframework.boot:spring-boot-starter-mustache")
  implementation("org.apache.tomcat.embed:tomcat-embed-core:10.1.25")
  implementation("com.github.spullara.mustache.java:compiler:0.9.5")
  implementation("com.github.jknack:handlebars:4.4.0")
  implementation("org.apache.commons:commons-lang3:3.12.0")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.14.2")
  implementation("io.sentry:sentry-spring-boot-starter-jakarta:6.34.0")
  implementation("io.sentry:sentry-logback:6.34.0")
  testImplementation("org.wiremock:wiremock-standalone:3.3.1")
  testImplementation("io.kotest:kotest-assertions-json-jvm:5.8.0")
  testImplementation("io.kotest:kotest-runner-junit5-jvm:5.8.0")
  testImplementation("io.kotest:kotest-assertions-core-jvm:5.8.0")
  testImplementation("io.kotest.extensions:kotest-extensions-spring:1.1.3")
  testImplementation("io.mockk:mockk:1.13.9")
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
