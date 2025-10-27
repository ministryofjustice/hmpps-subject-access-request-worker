plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "9.1.4"
  kotlin("plugin.spring") version "2.2.21"
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
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.json:json:20250517")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
  implementation("com.itextpdf:itext7-core:9.3.0")
  implementation("com.itextpdf:html2pdf:6.2.1")
  implementation("org.springframework.boot:spring-boot-starter-mustache")
  implementation("com.github.spullara.mustache.java:compiler:0.9.14")
  implementation("com.github.jknack:handlebars:4.5.0")
  implementation("org.apache.commons:commons-lang3:3.19.0")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.0")
  implementation("io.sentry:sentry-spring-boot-starter-jakarta:8.24.0")
  implementation("io.sentry:sentry-logback:8.24.0")
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.7.0")
  implementation("commons-io:commons-io:2.20.0")
  implementation("aws.sdk.kotlin:s3:1.5.68")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("com.h2database:h2:2.4.240")
  runtimeOnly("org.postgresql:postgresql:42.7.8")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  implementation("org.apache.commons:commons-csv:1.14.1")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.7.0")
  testImplementation("org.wiremock:wiremock-standalone:3.13.1")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("com.opencsv:opencsv:5.12.0")
  testImplementation("org.testcontainers:testcontainers:2.0.1")
  testImplementation("org.testcontainers:junit-jupiter:1.21.3")
}

kotlin {
  jvmToolchain(21)
}

springBoot {
  mainClass.set("uk.gov.justice.digital.hmpps.subjectaccessrequestworker.SubjectAccessRequestWorkerKt")
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
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
  register<TemplateGenerator>("generateHtml") {
    group = "templates"
    description = "Generate subject access report HTML for the specified service name"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateDevelopmentUtilKt"
    environment = mapOf("TEST_RESOURCES_DIR" to project.rootDir.resolve("src/main/resources"))
  }
}

abstract class TemplateGenerator : JavaExec() {
  private lateinit var serviceName: String

  @Option(
    option = "service",
    description = "The service name to generate report html for e.g 'hmpps-book-secure-move-api'",
  )
  fun setServiceName(serviceName: String) {
    this.serviceName = serviceName
  }

  @Input
  fun getServiceName(): String = serviceName

  @TaskAction
  fun generate() {
    args(this.serviceName)
    super.exec()
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
