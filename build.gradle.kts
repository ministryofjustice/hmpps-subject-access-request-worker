plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "6.0.8"
  kotlin("plugin.spring") version "2.0.21"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.json:json:20240303")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.9.0")
  implementation("com.itextpdf:itext7-core:8.0.5")
  implementation("com.itextpdf:html2pdf:5.0.5")
  implementation("org.springframework.boot:spring-boot-starter-mustache")
  implementation("com.github.spullara.mustache.java:compiler:0.9.14")
  implementation("com.github.jknack:handlebars:4.4.0")
  implementation("org.apache.commons:commons-lang3:3.17.0")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
  implementation("io.sentry:sentry-spring-boot-starter-jakarta:7.16.0")
  implementation("io.sentry:sentry-logback:7.16.0")
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.0.8")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.0.8")
  testImplementation("org.wiremock:wiremock-standalone:3.9.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}
