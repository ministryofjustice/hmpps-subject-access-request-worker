package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.createBucket
import aws.sdk.kotlin.services.s3.headBucket
import aws.sdk.kotlin.services.s3.model.BucketLocationConstraint
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private const val PROVIDER_AWS = "aws"

@ConfigurationProperties(prefix = "s3")
@ConditionalOnProperty(name = ["html-renderer.enabled"], havingValue = "true")
data class S3Properties(
  val region: String,
  val bucketName: String,
  val provider: String,
  val serviceEndpointOverride: String? = "http://localhost:4566",
)

@Configuration
@EnableConfigurationProperties(S3Properties::class)
@ConditionalOnProperty(name = ["html-renderer.enabled"], havingValue = "true")
class S3ClientConfig(
  private val s3Properties: S3Properties,
  @Value("\${html-renderer.enabled:false}") private val htmlRenderEnabled: Boolean,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  init {
    log.info("html-renderer.enabled={} s3.provider={}", htmlRenderEnabled, s3Properties.provider)
  }

  @Bean
  @ConditionalOnProperty(name = ["html-renderer.enabled"], havingValue = "true")
  fun s3(): S3Client {
    if (htmlRenderEnabled && PROVIDER_AWS == s3Properties.provider) {
      return s3Client()
    }
    return s3ClientLocalstack()
  }

  private fun s3Client(): S3Client = runBlocking {
    log.info("configuring S3 client for provider: ${s3Properties.provider}, region: ${s3Properties.region}")

    S3Client.fromEnvironment {
      region = s3Properties.region
    }
  }

  private fun s3ClientLocalstack(): S3Client = runBlocking {
    log.info("configuring S3 client for provider: ${s3Properties.provider}, region: ${s3Properties.region}")

    S3Client.fromEnvironment {
      region = s3Properties.region
      endpointUrl = Url.parse(s3Properties.serviceEndpointOverride!!)
      forcePathStyle = true
      credentialsProvider = StaticCredentialsProvider {
        accessKeyId = "test"
        secretAccessKey = "test"
      }
    }.also {
      createBucketIfNotExists(it)
    }
  }

  private suspend fun createBucketIfNotExists(s3: S3Client) {
    try {
      s3.headBucket { bucket = s3Properties.bucketName }.also {
        log.info("bucket {} exists no action required", s3Properties.bucketName)
      }
    } catch (ex: NotFound) {
      log.info("bucket {} not found, attempting to create", s3Properties.bucketName)

      s3.createBucket {
        bucket = s3Properties.bucketName
        createBucketConfiguration {
          locationConstraint = BucketLocationConstraint.fromValue(s3Properties.region)
        }
      }

      log.info("bucket {} successfully created", s3Properties.bucketName)
    }
  }
}
