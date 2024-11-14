package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.verify
import org.mockito.kotlin.times
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.STORE_DOCUMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestDocumentStoreConflictException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedErrorMessage
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.fileHash
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiExtension.Companion.documentApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import wiremock.org.apache.commons.io.IOUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.UUID

class DocumentStorageClientIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var documentStorageClient: DocumentStorageClient

  @Autowired
  private lateinit var webClientRetriesSpec: WebClientRetriesSpec

  @Autowired
  private lateinit var oAuth2AuthorizedClientService: OAuth2AuthorizedClientService

  private val subjectAccessRequestId = UUID.randomUUID()
  private val subjectAccessRequest = SubjectAccessRequest(id = subjectAccessRequestId)

  @MockBean
  private lateinit var telemetryClient: TelemetryClient

  companion object {
    private const val FILE_CONTENT =
      "Buddy you're a boy make a big noise, Playin' in the street gonna be a big man some day"

    private val objectMapper = ObjectMapper()

    @JvmStatic
    fun metadataFormats(): List<Any?> = listOf(
      null,
      "",
      1,
      listOf("A", "B", "C"),
      listOf(
        Pair("sarCaseReferenceNumber", "sarCaseReferenceNumber"),
        Pair("requestedDate", LocalDateTime.now().toString()),
      ),
      mapOf("key" to "value"),
      object {
        val outerObject = object {
          val innerObject = "wibble"
        }
      },
    )
  }

  @BeforeEach
  fun setup() {
    // Remove the cache client token to force each test to obtain an Auth token before calling the documentStore API.
    oAuth2AuthorizedClientService.removeAuthorizedClient("sar-client", "anonymousUser")

    hmppsAuth.stubGrantToken()
  }

  @Test
  fun `file upload success`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)
    val fileSize = expectedFileContent.toByteArray().size

    documentApi.stubUploadFileSuccess(subjectAccessRequestId.toString(), fileSize, FILE_CONTENT.toByteArray(), 1)

    val resp = documentStorageClient.storeDocument(subjectAccessRequest, expectedFileContent)

    val expectedResponse = expectedSuccessResponse(content = FILE_CONTENT.toByteArray())
    assertThat(resp).isEqualTo(expectedResponse)

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
    verifyFileSizeVerifySuccessTelemetryEvent(subjectAccessRequest, FILE_CONTENT.toByteArray())
  }

  @Test
  fun `store document does not retry when fails with a 401 status`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)

    documentApi.stubUploadFileFailsWithStatus(
      subjectAccessRequestId = subjectAccessRequestId.toString(),
      expectedFileContent = FILE_CONTENT.toByteArray(),
      status = 401,
    )

    val ex = assertThrows<FatalSubjectAccessRequestException> {
      documentStorageClient.storeDocument(subjectAccessRequest, expectedFileContent)
    }

    assertExpectedErrorMessage(
      actual = ex,
      prefix = "subjectAccessRequest failed with non-retryable error: client 4xx response status,",
      "event" to STORE_DOCUMENT,
      "id" to subjectAccessRequestId,
      "uri" to "${documentApi.baseUrl()}/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId",
      "httpStatus" to HttpStatus.UNAUTHORIZED,
    )

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
  }

  @Test
  fun `store document does not retry when fails with a 403 status`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)
    hmppsAuth.stubGrantToken()

    documentApi.stubUploadFileFailsWithStatus(
      subjectAccessRequestId = subjectAccessRequestId.toString(),
      expectedFileContent = FILE_CONTENT.toByteArray(),
      status = 403,
    )

    val ex = assertThrows<FatalSubjectAccessRequestException> {
      documentStorageClient.storeDocument(subjectAccessRequest, expectedFileContent)
    }

    assertExpectedErrorMessage(
      actual = ex,
      prefix = "subjectAccessRequest failed with non-retryable error: client 4xx response status,",
      "event" to STORE_DOCUMENT,
      "id" to subjectAccessRequestId,
      "uri" to "${documentApi.baseUrl()}/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId",
      "httpStatus" to HttpStatus.FORBIDDEN,
    )

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
  }

  @Test
  fun `store document does not retry when fails with a 409 status`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)

    documentApi.stubUploadFileFailsWithStatus(
      subjectAccessRequestId = subjectAccessRequestId.toString(),
      expectedFileContent = FILE_CONTENT.toByteArray(),
      status = 409,
    )

    val ex = assertThrows<SubjectAccessRequestDocumentStoreConflictException> {
      documentStorageClient.storeDocument(subjectAccessRequest, expectedFileContent)
    }

    assertExpectedErrorMessage(
      actual = ex,
      prefix = "subject access request document store upload unsuccessful: document already exists",
      "event" to STORE_DOCUMENT,
      "id" to subjectAccessRequestId,
      "uri" to "${documentApi.baseUrl()}/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId",
      "httpStatus" to HttpStatus.CONFLICT,
    )

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
  }

  @Test
  fun `store document retries on 5xx status`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)

    documentApi.stubUploadFileFailsWithStatus(
      subjectAccessRequestId.toString(),
      expectedFileContent.toByteArray(),
      500,
    )

    val ex = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      documentStorageClient.storeDocument(subjectAccessRequest, expectedFileContent)
    }

    assertExpectedErrorMessage(
      actual = ex,
      prefix = "subjectAccessRequest failed and max retry attempts (2) exhausted,",
      "event" to STORE_DOCUMENT,
      "id" to subjectAccessRequestId,
      "uri" to "/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId",
    )

    documentApi.verifyStoreDocumentIsCalled(
      times = 3,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
  }

  @Test
  fun `store document is successful when initial request fails with 5xx status and first retry succeeds`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)
    hmppsAuth.stubGrantToken()

    documentApi.stubUploadFileFailsWithStatusThenSucceedsOnRetry(
      subjectAccessRequestId.toString(),
      expectedFileContent.toByteArray(),
      500,
    )

    val resp = documentStorageClient.storeDocument(subjectAccessRequest, expectedFileContent)

    val expectedResponse = expectedSuccessResponse(content = FILE_CONTENT.toByteArray())
    assertThat(resp).isEqualTo(expectedResponse)

    documentApi.verifyStoreDocumentIsCalled(
      times = 2,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
  }

  @Test
  fun `store document retries on client request exception`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)
    val webClientWithIncorrectUrl = WebClient.builder()
      .baseUrl("http://localhost:${documentApi.port() + 1}")
      .build()

    documentStorageClient = DocumentStorageClient(webClientWithIncorrectUrl, webClientRetriesSpec, telemetryClient)

    val ex = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      documentStorageClient.storeDocument(subjectAccessRequest, expectedFileContent)
    }

    assertExpectedErrorMessage(
      actual = ex,
      prefix = "subjectAccessRequest failed and max retry attempts (2) exhausted,",
      "event" to STORE_DOCUMENT,
      "id" to subjectAccessRequest.id,
      "uri" to "/documents/SUBJECT_ACCESS_REQUEST_REPORT/${subjectAccessRequest.id}",
    )

    assertThat(ex.cause).isInstanceOf(WebClientRequestException::class.java)
    assertThat(ex.cause?.message).containsIgnoringCase("connection refused")

    documentApi.verifyNeverCalled()
  }

  @Test
  fun `documentStorageClient fails to obtain auth token with UNAUTHORIZED`() {
    hmppsAuth.stubUnauthorizedGrantToken()

    val expectedFileContent = getFileBytes(FILE_CONTENT)

    val ex = assertThrows<FatalSubjectAccessRequestException> {
      documentStorageClient.storeDocument(subjectAccessRequest, expectedFileContent)
    }

    assertExpectedErrorMessage(
      actual = ex,
      prefix = "subjectAccessRequest failed with non-retryable error: documentStoreClient error authorization exception,",
      "event" to STORE_DOCUMENT,
      "id" to subjectAccessRequestId,
      "cause" to expectedClientAuthError(401, "Unauthorized"),
    )

    assertThat(ex.cause).isInstanceOf(ClientAuthorizationException::class.java)

    hmppsAuth.verifyCalledOnce()
    documentApi.verifyNeverCalled()
  }

  @Test
  fun `documentStorageClient fails to obtain auth token with FORBIDDEN`() {
    hmppsAuth.stubForbiddenGrantToken()

    val expectedFileContent = getFileBytes(FILE_CONTENT)

    val ex = assertThrows<FatalSubjectAccessRequestException> {
      documentStorageClient.storeDocument(subjectAccessRequest, expectedFileContent)
    }

    assertExpectedErrorMessage(
      actual = ex,
      prefix = "subjectAccessRequest failed with non-retryable error: documentStoreClient error authorization exception,",
      "event" to STORE_DOCUMENT,
      "id" to subjectAccessRequestId,
      "cause" to expectedClientAuthError(403, "Forbidden"),
    )

    assertThat(ex.cause).isInstanceOf(ClientAuthorizationException::class.java)

    hmppsAuth.verifyCalledOnce()
    documentApi.verifyNeverCalled()
  }

  @Test
  fun `documentStorageClient fails to obtain auth token with INTERNAL_SERVER_ERROR`() {
    hmppsAuth.stubServerErrorGrantToken()

    val expectedFileContent = getFileBytes(FILE_CONTENT)

    val ex = assertThrows<FatalSubjectAccessRequestException> {
      documentStorageClient.storeDocument(subjectAccessRequest, expectedFileContent)
    }

    assertExpectedErrorMessage(
      actual = ex,
      prefix = "subjectAccessRequest failed with non-retryable error: documentStoreClient error authorization exception,",
      "event" to STORE_DOCUMENT,
      "id" to subjectAccessRequestId,
      "cause" to expectedClientAuthError(500, "Server Error"),
    )

    assertThat(ex.cause).isInstanceOf(ClientAuthorizationException::class.java)

    hmppsAuth.verifyCalledOnce()
    documentApi.verifyNeverCalled()
  }

  @Test
  fun `uploaded document file size does not match expected content size throw exception`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)
    val incorrectFileSize = expectedFileContent.toByteArray().size * 2

    documentApi.stubUploadFileSuccess(
      subjectAccessRequestId.toString(),
      incorrectFileSize,
      FILE_CONTENT.toByteArray(),
      1,
    )

    val ex = assertThrows<SubjectAccessRequestException> {
      documentStorageClient.storeDocument(subjectAccessRequest, expectedFileContent)
    }

    assertExpectedErrorMessage(
      actual = ex,
      prefix = "document store upload error: response file size did not match the expected file upload size",
      "event" to STORE_DOCUMENT,
      "id" to subjectAccessRequestId,
      "expectedFileSize" to expectedFileContent.toByteArray().size,
      "actualFileSize" to incorrectFileSize,
      "documentUuid" to subjectAccessRequestId,
      "documentFileHash" to fileHash(expectedFileContent.toByteArray()),
    )

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )

    verifyFileSizeVerifyErrorTelemetryEvent(
      subjectAccessRequest,
      FILE_CONTENT.toByteArray(),
      incorrectFileSize,
    )
  }

  @Test
  fun `document store upload success invalid response body throws expected exception`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)

    documentApi.stubUploadFileReturnsInvalidResponseEntity(
      subjectAccessRequestId.toString(),
      FILE_CONTENT.toByteArray(),
    )

    val ex = assertThrows<SubjectAccessRequestException> {
      documentStorageClient.storeDocument(subjectAccessRequest, expectedFileContent)
    }

    assertExpectedErrorMessage(
      actual = ex,
      prefix = "documentStoreClient unexpected error",
      "event" to STORE_DOCUMENT,
      "id" to subjectAccessRequestId,
    )

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
  }

  @ParameterizedTest()
  @MethodSource("metadataFormats")
  fun `store document will successfully handle response metadata of different value types`(metadata: Any?) {
    val expectedFileContent = getFileBytes(FILE_CONTENT)
    val fileSize = expectedFileContent.toByteArray().size

    documentApi.stubUploadFileSuccess(
      subjectAccessRequestId = subjectAccessRequestId.toString(),
      fileSize = fileSize,
      expectedFileContent = FILE_CONTENT.toByteArray(),
      metadata = metadata,
    )

    val resp = documentStorageClient.storeDocument(subjectAccessRequest, expectedFileContent)

    val expectedResponse =
      expectedSuccessResponseWithMetadata(content = FILE_CONTENT.toByteArray(), metadata = metadata)
    assertThat(resp).isEqualTo(expectedResponse)

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
    verifyFileSizeVerifySuccessTelemetryEvent(subjectAccessRequest, FILE_CONTENT.toByteArray())
  }

  fun getFileBytes(input: String): ByteArrayOutputStream {
    val baos = ByteArrayOutputStream()
    IOUtils.copy(ByteArrayInputStream(input.toByteArray()), baos)
    return baos
  }

  fun expectedClientAuthError(status: Int, description: String): String {
    return "[invalid_token_response] An error occurred while attempting to retrieve the OAuth 2.0 Access Token Response: $status $description: [no body]"
  }

  fun expectedSuccessResponse(fileSize: Int? = null, content: ByteArray): DocumentStorageClient.PostDocumentResponse {
    return objectMapper.readValue(
      documentApi.documentUploadSuccessResponseJson(
        subjectAccessRequestId.toString(),
        fileSize ?: content.size,
        content,
      ),
      DocumentStorageClient.PostDocumentResponse::class.java,
    )
  }

  fun expectedSuccessResponseWithMetadata(
    fileSize: Int? = null,
    content: ByteArray,
    metadata: Any?,
  ): DocumentStorageClient.PostDocumentResponse {
    return objectMapper.readValue(
      documentApi.documentUploadSuccessResponseJsonWithMetadata(
        subjectAccessRequestId.toString(),
        fileSize ?: content.size,
        content,
        metadata,
      ),
      DocumentStorageClient.PostDocumentResponse::class.java,
    )
  }

  fun verifyFileSizeVerifySuccessTelemetryEvent(subjectAccessRequest: SubjectAccessRequest, content: ByteArray) {
    val props = mapOf(
      "sarId" to subjectAccessRequest.sarCaseReferenceNumber,
      "UUID" to subjectAccessRequest.id.toString(),
      "expectedFileSize" to content.size.toString(),
      "actualFileSize" to content.size.toString(),
      "documentUuid" to subjectAccessRequest.id.toString(),
      "documentFileHash" to fileHash(content),
    )

    verify(telemetryClient, times(1))
      .trackEvent("documentUploadFileSizeVerificationSuccess", props, null)
  }

  fun verifyFileSizeVerifyErrorTelemetryEvent(
    subjectAccessRequest: SubjectAccessRequest,
    content: ByteArray,
    actualSize: Int,
  ) {
    val props = mapOf(
      "sarId" to subjectAccessRequest.sarCaseReferenceNumber,
      "UUID" to subjectAccessRequest.id.toString(),
      "expectedFileSize" to content.size.toString(),
      "actualFileSize" to actualSize.toString(),
      "documentUuid" to subjectAccessRequest.id.toString(),
      "documentFileHash" to fileHash(content),
    )

    verify(telemetryClient, times(1))
      .trackEvent("documentUploadFileSizeVerificationError", props, null)
  }
}
