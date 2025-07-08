package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.deleteObjects
import aws.sdk.kotlin.services.s3.headObject
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.TestComponent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.S3Properties

@TestComponent
@ConditionalOnProperty(name = ["html-renderer.enabled"], havingValue = "true")
class S3TestUtils(
  val s3: S3Client,
  val s3Properties: S3Properties,
) {

  data class S3File(val key: String, val content: String)
  data class S3AttachmentFile(
    val key: String,
    val content: ByteArray,
    val contentType: String,
    val contentLength: Long,
    val filename: String,
    val attachmentNumber: Int,
    val name: String,
  )

  fun putFile(file: S3AttachmentFile) = runBlocking {
    s3.putObject {
      bucket = s3Properties.bucketName
      key = file.key
      body = ByteStream.fromBytes(file.content)
      contentType = file.contentType
      contentLength = file.contentLength
      metadata = mapOf(
        "x-amz-meta-filename" to file.filename,
        "x-amz-meta-attachment-number" to file.attachmentNumber.toString(),
        "x-amz-meta-name" to file.name,
      )
    }
  }

  fun putFile(file: S3File) = runBlocking {
    s3.putObject {
      bucket = s3Properties.bucketName
      key = file.key
      body = ByteStream.fromString(file.content)
    }
  }

  fun clearBucket() = runBlocking {
    s3.listObjectsV2 { bucket = s3Properties.bucketName }
      .contents
      ?.map { ObjectIdentifier { key = it.key } }
      ?.takeIf { it.isNotEmpty() }
      ?.let { identifiers ->
        s3.deleteObjects {
          bucket = s3Properties.bucketName
          delete = Delete {
            objects = identifiers
          }
        }
      }
  }

  fun documentExists(key: String): Boolean = runBlocking {
    try {
      s3.headObject {
        this.bucket = s3Properties.bucketName
        this.key = key
      }
      true
    } catch (e: NotFound) {
      false
    }
  }
}
