package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectResponse
import aws.smithy.kotlin.runtime.content.toInputStream
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.S3Properties
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_RENDERED_HTML_DOCUMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.io.ByteArrayInputStream
import java.io.InputStream

@Service
class HtmlDocumentStoreService(
  private val s3: S3Client,
  private val s3Properties: S3Properties,
) {
  suspend fun getDocument(subjectAccessRequest: SubjectAccessRequest, serviceName: String): InputStream? {
    return try {
      s3.getObject(
        GetObjectRequest {
          bucket = s3Properties.bucketName
          key = "${subjectAccessRequest.id}/${serviceName}.html"
        },
      ) { getResponseAsInputStream(it) }
    } catch (ex: Exception) {
      throw getDocumentException(subjectAccessRequest, ex, serviceName)
    }
  }

  private fun getResponseAsInputStream(response: GetObjectResponse) = response.body
    ?.toInputStream()
    ?: ByteArrayInputStream(ByteArray(0))

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
      "documentKey" to "${subjectAccessRequest.id}/${serviceName}.html",
    ),
  )
}