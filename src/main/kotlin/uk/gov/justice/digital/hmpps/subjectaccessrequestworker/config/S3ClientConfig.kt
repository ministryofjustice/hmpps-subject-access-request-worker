package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import aws.sdk.kotlin.services.s3.S3Client
import kotlinx.coroutines.runBlocking
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "s3")
data class S3Properties(
  val region: String,
  val bucketName: String,
  val serviceEndpointOverride: String?,
)

@Configuration
@EnableConfigurationProperties(S3Properties::class)
class S3ClientConfig(private val s3Properties: S3Properties) {

  @Bean
  fun s3Client(): S3Client = runBlocking {
    S3Client.fromEnvironment {
      region = s3Properties.region
    }
  }
}