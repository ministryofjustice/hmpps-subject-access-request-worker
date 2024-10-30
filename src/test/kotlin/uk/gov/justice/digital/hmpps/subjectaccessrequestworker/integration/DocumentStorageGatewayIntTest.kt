package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.STORE_DOCUMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.DocumentStorageGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.HmppsAuthGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiMockExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiMockExtension.Companion.documentApiMock
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import wiremock.org.apache.commons.io.IOUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

@ExtendWith(DocumentApiMockExtension::class)
class DocumentStorageGatewayIntTest : IntegrationTestBase() {

  @MockBean
  private lateinit var authGateway: HmppsAuthGateway

  @Autowired
  protected lateinit var webClientRetriesSpec: WebClientRetriesSpec

  private lateinit var documentStorageGateway: DocumentStorageGateway

  private lateinit var subjectAccessRequestId: UUID

  companion object {
    const val AUTH_TOKEN = "auth-token"

    private const val FILE_CONTENT =
      "Buddy you're a boy make a big noise, Playin' in the street gonna be a big man some day"
  }

  @BeforeEach
  fun setup() {
    this.subjectAccessRequestId = UUID.randomUUID()

    whenever(authGateway.getClientToken()).thenReturn(AUTH_TOKEN)

    documentStorageGateway = DocumentStorageGateway(
      authGateway,
      documentApiMock.baseUrl(),
      webClientRetriesSpec,
    )
  }

  @Test
  fun `file upload success`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)
    documentApiMock.stubUploadFileSuccess(subjectAccessRequestId.toString(), AUTH_TOKEN, FILE_CONTENT.toByteArray())

    val resp = documentStorageGateway.storeDocument(subjectAccessRequestId, expectedFileContent)

    assertThat(resp).isEqualTo(
      documentApiMock.documentUploadSuccessResponseJson(subjectAccessRequestId.toString()),
    )

    documentApiMock.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
      authToken = AUTH_TOKEN,
    )
  }

  @Test
  fun `store document does not retry when fails with a 4xx status`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)

    documentApiMock.stubUploadFileFailsWithStatus(
      subjectAccessRequestId = subjectAccessRequestId.toString(),
      authToken = AUTH_TOKEN,
      expectedFileContent = FILE_CONTENT.toByteArray(),
      status = 400,
    )

    val ex = assertThrows<FatalSubjectAccessRequestException> {
      documentStorageGateway.storeDocument(subjectAccessRequestId, expectedFileContent)
    }

    assertExpectedErrorMessage(
      actual = ex,
      prefix = "subjectAccessRequest failed with non-retryable error: client 4xx response status,",
      "event" to STORE_DOCUMENT,
      "id" to subjectAccessRequestId,
      "uri" to "${documentApiMock.baseUrl()}/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId",
      "httpStatus" to HttpStatus.BAD_REQUEST,
    )

    documentApiMock.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
      authToken = AUTH_TOKEN,
    )
  }

  fun getFileBytes(input: String): ByteArrayOutputStream {
    val baos = ByteArrayOutputStream()
    IOUtils.copy(ByteArrayInputStream(input.toByteArray()), baos)
    return baos
  }
}
