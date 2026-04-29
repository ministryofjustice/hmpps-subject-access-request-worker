plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "10.2.3"
  kotlin("plugin.spring") version "2.3.21"
  kotlin("plugin.jpa") version "2.3.21"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencyCheck {
  suppressionFiles.add("hmpps-sar-worker-suppressions.xml")
}

// okhttp only used by the AWS SDK kotlin library so okay to pin
ext["okhttp.version"] = "5.0.0-alpha.14"
ext["kotlin-coroutines.version"] = "1.10.2"

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:2.1.1")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.json:json:20251224")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
  implementation("com.itextpdf:itext7-core:9.6.0")
  implementation("com.itextpdf:html2pdf:6.3.2")
  implementation("org.springframework.boot:spring-boot-starter-mustache")
  implementation("org.springframework.boot:spring-boot-jackson2")
  implementation("com.github.spullara.mustache.java:compiler:0.9.14")
  implementation("com.github.jknack:handlebars:4.5.0")
  implementation("org.apache.commons:commons-lang3:3.20.0")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.2")
  implementation("io.sentry:sentry-spring-boot-4:8.40.0")
  implementation("io.sentry:sentry-logback:8.40.0")
  implementation("commons-io:commons-io:2.22.0")
  implementation("aws.sdk.kotlin:s3:1.6.65")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
  constraints {
    implementation("org.webjars:swagger-ui:5.32.2")
  }
  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("com.h2database:h2:2.4.240")
  runtimeOnly("org.postgresql:postgresql:42.7.10")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  implementation("org.apache.commons:commons-csv:1.14.1")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")
  implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:2.0.0")
  testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webclient-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("com.opencsv:opencsv:5.12.0")
  testImplementation("org.testcontainers:testcontainers:2.0.3")
  testImplementation("org.testcontainers:junit-jupiter:1.21.4")
}

kotlin {
  jvmToolchain(25)
}

springBoot {
  mainClass.set("uk.gov.justice.digital.hmpps.subjectaccessrequestworker.SubjectAccessRequestWorkerKt")
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }
  register<BacklogRequestImport>("importBacklog") {
    group = "backlog"
    description = "Import SAR backlog requests from a CSV input file"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "uk.gov.justice.digital.hmpps.subjectaccessrequestworker.backlog.utils.BacklogRequestImporterKt"
    environment = mapOf(
      "IMPORT_REPORT_CSV" to project.rootDir.resolve("src/main/resources/backlog-import-results.csv"),
      "ERROR_LOG" to project.rootDir.resolve("src/main/resources/backlog-import-errors.csv"),
    )
  }
}

abstract class BacklogRequestImport : JavaExec() {
  private lateinit var token: String
  private lateinit var csv: String
  private lateinit var version: String
  private var env: String? = null

  @Option(
    option = "token",
    description = "preprod auth token",
  )
  fun setToken(token: String) {
    this.token = token
  }

  @Option(
    option = "csv",
    description = "Input CSV file of requests to import",
  )
  fun setCsv(csv: String) {
    this.csv = csv
  }

  @Option(
    option = "importVersion",
    description = "The version to assign to the imported requests",
  )
  fun setVersion(version: String) {
    this.version = version
  }

  @Option(
    option = "env",
    description = "target env for the script",
  )
  fun setEnv(env: String) {
    this.env = env
  }

  @Input
  fun getToken(): String = token

  @Input
  fun getCsv(): String = csv

  @Input
  fun getVersion(): String = version

  @Optional
  @Input
  fun getEnv(): String? = env

  @TaskAction
  fun import() {
    args(this.version, this.csv, this.token, this.env ?: "dev")
  }
}
