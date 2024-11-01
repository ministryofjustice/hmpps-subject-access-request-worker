package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.STORE_DOCUMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedErrorMessage
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiExtension.Companion.documentApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import wiremock.org.apache.commons.io.IOUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

class DocumentStorageClientIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var documentStorageClient: DocumentStorageClient

  @Autowired
  private lateinit var webClientRetriesSpec: WebClientRetriesSpec

  @Autowired
  private lateinit var oAuth2AuthorizedClientService: OAuth2AuthorizedClientService

  private val subjectAccessRequestId = UUID.randomUUID()

  companion object {
    private const val FILE_CONTENT =
      "Buddy you're a boy make a big noise, Playin' in the street gonna be a big man some day"
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

    documentApi.stubUploadFileSuccess(subjectAccessRequestId.toString(), FILE_CONTENT.toByteArray())

    val resp = documentStorageClient.storeDocument(subjectAccessRequestId, expectedFileContent)

    assertThat(resp).isEqualTo(
      documentApi.documentUploadSuccessResponseJson(subjectAccessRequestId.toString()),
    )

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
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
      documentStorageClient.storeDocument(subjectAccessRequestId, expectedFileContent)
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
      documentStorageClient.storeDocument(subjectAccessRequestId, expectedFileContent)
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
  fun `store document retries on 5xx status`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)
    val subjectAccessRequestId = UUID.randomUUID()

    documentApi.stubUploadFileFailsWithStatus(
      subjectAccessRequestId.toString(),
      expectedFileContent.toByteArray(),
      500,
    )

    val ex = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      documentStorageClient.storeDocument(subjectAccessRequestId, expectedFileContent)
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
    val subjectAccessRequestId = UUID.randomUUID()
    hmppsAuth.stubGrantToken()

    documentApi.stubUploadFileFailsWithStatusThenSucceedsOnRetry(
      subjectAccessRequestId.toString(),
      expectedFileContent.toByteArray(),
      500,
    )

    val resp = documentStorageClient.storeDocument(subjectAccessRequestId, expectedFileContent)

    assertThat(resp).isEqualTo(
      documentApi.documentUploadSuccessResponseJson(subjectAccessRequestId.toString()),
    )

    documentApi.verifyStoreDocumentIsCalled(
      times = 2,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
  }

  @Test
  fun `store document retries on client request exception`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)
    val subjectAccessRequestId = UUID.randomUUID()
    val webClientWithIncorrectUrl = WebClient.builder()
      .baseUrl("http://localhost:${documentApi.port() + 1}")
      .build()

    documentStorageClient = DocumentStorageClient(webClientWithIncorrectUrl, webClientRetriesSpec)

    val ex = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      documentStorageClient.storeDocument(subjectAccessRequestId, expectedFileContent)
    }

    assertExpectedErrorMessage(
      actual = ex,
      prefix = "subjectAccessRequest failed and max retry attempts (2) exhausted,",
      "event" to STORE_DOCUMENT,
      "id" to subjectAccessRequestId,
      "uri" to "/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId",
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
      documentStorageClient.storeDocument(subjectAccessRequestId, expectedFileContent)
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
      documentStorageClient.storeDocument(subjectAccessRequestId, expectedFileContent)
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
      documentStorageClient.storeDocument(subjectAccessRequestId, expectedFileContent)
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

  fun getFileBytes(input: String): ByteArrayOutputStream {
    val baos = ByteArrayOutputStream()
    IOUtils.copy(ByteArrayInputStream(input.toByteArray()), baos)
    return baos
  }

  fun expectedClientAuthError(status: Int, description: String): String {
    return "[invalid_token_response] An error occurred while attempting to retrieve the OAuth 2.0 Access Token Response: $status $description: [no body]"
  }
}
