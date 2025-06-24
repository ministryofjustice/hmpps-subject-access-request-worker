plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "8.3.0"
  kotlin("plugin.spring") version "2.2.0"
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
  implementation("org.json:json:20250517")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
  implementation("com.itextpdf:itext7-core:9.2.0")
  implementation("com.itextpdf:html2pdf:6.2.0")
  implementation("org.springframework.boot:spring-boot-starter-mustache")
  implementation("com.github.spullara.mustache.java:compiler:0.9.14")
  implementation("com.github.jknack:handlebars:4.4.0")
  implementation("org.apache.commons:commons-lang3:3.17.0")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.1")
  implementation("io.sentry:sentry-spring-boot-starter-jakarta:8.14.0")
  implementation("io.sentry:sentry-logback:8.14.0")
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.4.7")
  implementation("commons-io:commons-io:2.19.0")
  implementation("aws.sdk.kotlin:s3:1.4.111")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")
  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("com.h2database:h2:2.3.232")
  runtimeOnly("org.postgresql:postgresql:42.7.7")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.4.7")
  testImplementation("org.wiremock:wiremock-standalone:3.13.1")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("com.google.code.gson:gson:2.13.1")
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}

tasks.register<TemplateGenerator>("generateHtml") {
  group = "templates"
  description = "Generate subject access report HTML for the specified service name"
  classpath = sourceSets["test"].runtimeClasspath
  mainClass = "uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateDevelopmentUtilKt"
  environment = mapOf("TEST_RESOURCES_DIR" to project.rootDir.resolve("src/test/resources"))
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
