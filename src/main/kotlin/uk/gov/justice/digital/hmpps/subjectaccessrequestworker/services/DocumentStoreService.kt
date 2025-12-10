package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headObject
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectResponse
import aws.smithy.kotlin.runtime.content.toByteArray
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.S3Properties
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_ATTACHMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_RENDERED_HTML_DOCUMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_SERVICE_TEMPLATE_VERSION
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.LIST_ATTACHMENTS
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.ErrorCode.Companion.S3_GET_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.ErrorCode.Companion.S3_HEAD_OBJECT_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.ErrorCode.Companion.S3_LIST_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.io.ByteArrayInputStream
import java.io.InputStream

@Service
class DocumentStoreService(
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

  suspend fun listAttachments(subjectAccessRequest: SubjectAccessRequest, serviceName: String): List<AttachmentInfo> = try {
    s3.listObjectsV2 {
      this.bucket = s3Properties.bucketName
      this.prefix = "${subjectAccessRequest.id}/$serviceName/attachments/"
    }.contents?.map { s3Object ->
      val headResponse = s3.headObject {
        this.bucket = s3Properties.bucketName
        this.key = s3Object.key
      }
      AttachmentInfo(
        key = s3Object.key!!,
        attachmentNumber = headResponse.metadata?.get("x-amz-meta-attachment-number")!!.toInt(),
        name = headResponse.metadata?.get("x-amz-meta-name")!!,
        contentType = headResponse.contentType!!,
        filesize = headResponse.contentLength!!.toInt(),
        filename = headResponse.metadata?.get("x-amz-meta-filename")!!,
      )
    }?.sortedBy { it.attachmentNumber } ?: emptyList<AttachmentInfo>()
  } catch (ex: Exception) {
    throw SubjectAccessRequestException(
      message = "failed to list attachments from bucket",
      event = LIST_ATTACHMENTS,
      errorCode = S3_LIST_ERROR,
      subjectAccessRequest = subjectAccessRequest,
      cause = ex,
      params = mapOf(
        "serviceName" to serviceName,
        "prefix" to "${subjectAccessRequest.id}/$serviceName/attachments/",
      ),
    )
  }

  suspend fun getAttachment(subjectAccessRequest: SubjectAccessRequest, serviceName: String, attachmentInfo: AttachmentInfo): Attachment = try {
    val data = s3.getObject(
      GetObjectRequest {
        bucket = s3Properties.bucketName
        key = attachmentInfo.key
      },
    ) { ByteArrayInputStream(getResponseAsInputStream(it)) }
    Attachment(
      data = data,
      info = attachmentInfo,
    )
  } catch (ex: Exception) {
    throw SubjectAccessRequestException(
      message = "failed to get attachment from bucket",
      event = GET_ATTACHMENT,
      errorCode = S3_GET_ERROR,
      subjectAccessRequest = subjectAccessRequest,
      cause = ex,
      params = mapOf(
        "serviceName" to serviceName,
        "attachmentKey" to attachmentInfo.key,
      ),
    )
  }

  suspend fun getTemplateVersion(subjectAccessRequest: SubjectAccessRequest, serviceName: String): String = try {
    s3.headObject {
      bucket = s3Properties.bucketName
      key = "${subjectAccessRequest.id}/$serviceName.html"
    }.metadata?.get("template_version") ?: "NA"
  } catch (ex: Exception) {
    throw SubjectAccessRequestException(
      message = "failed to get template version from html document metadata",
      event = GET_SERVICE_TEMPLATE_VERSION,
      subjectAccessRequest = subjectAccessRequest,
      errorCode = S3_HEAD_OBJECT_ERROR,
      cause = ex,
      params = mapOf(
        "serviceName" to serviceName,
        "documentKey" to "${subjectAccessRequest.id}/$serviceName.html",
      ),
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
    errorCode = S3_GET_ERROR,
    cause = cause,
    params = mapOf(
      "serviceName" to serviceName,
      "documentKey" to "${subjectAccessRequest.id}/$serviceName.html",
    ),
  )
}

data class Attachment(
  val data: InputStream,
  val info: AttachmentInfo,
)

data class AttachmentInfo(
  val key: String,
  val attachmentNumber: Int,
  val name: String,
  val contentType: String,
  val filesize: Int,
  val filename: String,
)
