package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.STORE_DOCUMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import java.io.ByteArrayOutputStream

@Service
class DocumentStorageClient(
  private val documentStorageWebClient: WebClient,
  val webClientRetriesSpec: WebClientRetriesSpec,
  val telemetryClient: TelemetryClient,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    private const val UPLOAD_DOCUMENT_PATH = "/documents/SUBJECT_ACCESS_REQUEST_REPORT"
    private const val SAR_FILENAME = "report.pdf"
    private const val FILE_SIZE_VERIFY_ERROR_EVENT = "documentUploadFileSizeVerificationError"
    private const val FILE_SIZE_VERIFY_SUCCESS_EVENT = "documentUploadFileSizeVerificationSuccess"
  }

  fun storeDocument(subjectAccessRequest: SubjectAccessRequest, docBody: ByteArrayOutputStream): PostDocumentResponse {
    log.info("Storing document with UUID ${subjectAccessRequest.id}")
    val expectedSize = docBody.toByteArray().size
    val postDocumentResponse = executeStoreDocumentRequest(subjectAccessRequest, contentAsResource(docBody))
    return verifyUploadedFileSize(postDocumentResponse, subjectAccessRequest, expectedSize)
  }

  private fun contentAsResource(docBody: ByteArrayOutputStream) = object : ByteArrayResource(docBody.toByteArray()) {
    override fun getFilename(): String = SAR_FILENAME
  }

  private fun executeStoreDocumentRequest(
    subjectAccessRequest: SubjectAccessRequest,
    contentsAsResource: ByteArrayResource,
  ): PostDocumentResponse? {
    val subjectAccessRequestId = subjectAccessRequest.id
    try {
      return documentStorageWebClient.post().uri("$UPLOAD_DOCUMENT_PATH/$subjectAccessRequestId")
        .header("Service-Name", "DPS-Subject-Access-Requests")
        .bodyValue(
          MultipartBodyBuilder().apply {
            part("file", contentsAsResource)
            part(
              "metadata",
              listOf<Pair<String, Any?>>(
                Pair("sarCaseReferenceNumber", subjectAccessRequest.sarCaseReferenceNumber),
                Pair("requestedDate", subjectAccessRequest.requestDateTime.toString()),
              ),
            )
          }.build(),
        )
        .retrieve()
        .onStatus(
          webClientRetriesSpec.is409Conflict(),
          webClientRetriesSpec.throwDocumentApiConflictException(subjectAccessRequest),
        )
        .onStatus(
          webClientRetriesSpec.is4xxStatus(),
          webClientRetriesSpec.throw4xxStatusFatalError(STORE_DOCUMENT, subjectAccessRequest),
        )
        .bodyToMono(PostDocumentResponse::class.java)
        .retryWhen(
          webClientRetriesSpec.retry5xxAndClientRequestErrors(
            STORE_DOCUMENT,
            subjectAccessRequest,
            params = mapOf(
              "uri" to "$UPLOAD_DOCUMENT_PATH/$subjectAccessRequestId",
            ),
          ),
        ).block()
    } catch (ex: ClientAuthorizationException) {
      /**
       * Authentication error occur before making the documentStore request so must be caught/handle separately.
       */
      throw FatalSubjectAccessRequestException(
        message = "documentStoreClient error authorization exception",
        cause = ex,
        event = STORE_DOCUMENT,
        subjectAccessRequest = subjectAccessRequest,
      )
    } catch (ex: Exception) {
      if (ex is SubjectAccessRequestException) {
        // No action required, rethrow error
        throw ex
      }

      throw SubjectAccessRequestException(
        message = "documentStoreClient unexpected error",
        cause = ex,
        event = STORE_DOCUMENT,
        subjectAccessRequest = subjectAccessRequest,
      )
    }
  }

  private fun verifyUploadedFileSize(
    postDocumentResponse: PostDocumentResponse?,
    subjectAccessRequest: SubjectAccessRequest,
    expectedFileSize: Int,
  ): PostDocumentResponse {
    if (postDocumentResponse == null) {
      throw SubjectAccessRequestException(
        message = "document store upload error: response body expected but was null",
        cause = null,
        event = STORE_DOCUMENT,
        subjectAccessRequest = subjectAccessRequest,
      )
    }

    if (postDocumentResponse.fileSize != expectedFileSize) {
      telemetryClient.trackSarEvent(
        FILE_SIZE_VERIFY_ERROR_EVENT,
        subjectAccessRequest,
        "expectedFileSize" to expectedFileSize.toString(),
        "actualFileSize" to postDocumentResponse.fileSize.toString(),
        "documentUuid" to postDocumentResponse.documentUuid.toString(),
        "documentFileHash" to postDocumentResponse.fileHash.toString(),
      )

      throw SubjectAccessRequestException(
        message = "document store upload error: response file size did not match the expected file upload size",
        cause = null,
        event = STORE_DOCUMENT,
        subjectAccessRequest = subjectAccessRequest,
        params = mapOf(
          "expectedFileSize" to expectedFileSize,
          "actualFileSize" to postDocumentResponse.fileSize,
          "documentUuid" to postDocumentResponse.documentUuid,
          "documentFileHash" to postDocumentResponse.fileHash,
        ),
      )
    }

    telemetryClient.trackSarEvent(
      FILE_SIZE_VERIFY_SUCCESS_EVENT,
      subjectAccessRequest,
      "expectedFileSize" to expectedFileSize.toString(),
      "actualFileSize" to postDocumentResponse.fileSize.toString(),
      "documentUuid" to postDocumentResponse.documentUuid.toString(),
      "documentFileHash" to postDocumentResponse.fileHash.toString(),
    )
    return postDocumentResponse
  }

  data class PostDocumentResponse(
    val documentUuid: String? = null,
    val documentType: String? = null,
    val documentFilename: String? = null,
    val filename: String? = null,
    val fileExtension: String? = null,
    val fileSize: Int? = null,
    val fileHash: String? = null,
    val mimeType: String? = null,
    val metadata: Any? = null,
    val createdTime: String? = null,
    val createdByServiceName: String? = null,
    val createdByUsername: String? = null,
  )
}
