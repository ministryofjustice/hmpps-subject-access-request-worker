package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headObject
import aws.sdk.kotlin.services.s3.listObjects
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectResponse
import aws.smithy.kotlin.runtime.content.toByteArray
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.S3Properties
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_RENDERED_HTML_DOCUMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.String

@Service
@ConditionalOnProperty(name = ["html-renderer.enabled"], havingValue = "true")
class HtmlDocumentStoreService(
  private val s3: S3Client,
  private val s3Properties: S3Properties,
) {
  suspend fun getDocument(subjectAccessRequest: SubjectAccessRequest, serviceName: String): InputStream? = try {
    s3.getObject(
      GetObjectRequest {
        bucket = s3Properties.bucketName
        key = "${subjectAccessRequest.id}/$serviceName.html"
      },
    ) { ByteArrayInputStream(getResponseAsInputStream(it)) }
  } catch (ex: Exception) {
    throw getDocumentException(subjectAccessRequest, ex, serviceName)
  }

  suspend fun listAttachments(subjectAccessRequest: SubjectAccessRequest, serviceName: String): List<String> =
    s3.listObjects {
      this.bucket = s3Properties.bucketName
      this.prefix = "${subjectAccessRequest.id}/$serviceName/attachments/"
    }.contents?.map { s3Object ->
      val metadata = s3.headObject {
        this.bucket = s3Properties.bucketName
        this.key = s3Object.key
      }.metadata
      Pair(s3Object, metadata)
    }?.sortedBy { it.second?.get("x-amz-meta-attachment-ref") }?.map { (s3Object, _) -> s3Object.key!! } ?: emptyList<String>()

  suspend fun getAttachment(attachmentKey: String): Attachment {
    val headResponse = s3.headObject {
      this.bucket = s3Properties.bucketName
      this.key = attachmentKey
    }
    val filesize = headResponse.contentLength!!
    val contentType = headResponse.contentType!!
    val filename = headResponse.metadata?.get("x-amz-meta-filename")!!
    val attachmentRef = headResponse.metadata?.get("x-amz-meta-attachment-ref")!!
    val name = headResponse.metadata?.get("x-amz-meta-name")!!
    val data = s3.getObject(
      GetObjectRequest {
        bucket = s3Properties.bucketName
        key = attachmentKey
      },
    ) { ByteArrayInputStream(getResponseAsInputStream(it)) }
    return Attachment(
      attachmentRef = attachmentRef,
      name = name,
      contentType = contentType,
      data = data,
      filesize = filesize,
      filename = filename,
    )
  }

  private suspend fun getResponseAsInputStream(response: GetObjectResponse) = response.body
    ?.toByteArray() ?: ByteArray(0)

  private fun getDocumentException(
    subjectAccessRequest: SubjectAccessRequest,
    cause: Exception,
    serviceName: String,
  ): SubjectAccessRequestException = SubjectAccessRequestException(
    message = "failed to get html document from bucket",
    event = GET_RENDERED_HTML_DOCUMENT,
    subjectAccessRequest = subjectAccessRequest,
    cause = cause,
    params = mapOf(
      "serviceName" to serviceName,
      "documentKey" to "${subjectAccessRequest.id}/$serviceName.html",
    ),
  )
}

data class Attachment (
  val attachmentRef: String,
  val name: String,
  val contentType: String,
  val data: InputStream,
  val filesize: Long,
  val filename: String,
)
