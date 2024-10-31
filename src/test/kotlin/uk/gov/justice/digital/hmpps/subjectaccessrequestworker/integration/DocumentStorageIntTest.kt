package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.STORE_DOCUMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiExtension.Companion.documentApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import wiremock.org.apache.commons.io.IOUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

class DocumentStorageIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var documentStorageClient: DocumentStorageClient

  companion object {
    private const val FILE_CONTENT =
      "Buddy you're a boy make a big noise, Playin' in the street gonna be a big man some day"
  }

  @Test
  fun `file upload success`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)
    val subjectAccessRequestId = UUID.randomUUID()
    hmppsAuth.stubGrantToken()
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
  fun `store document does not retry when fails with a 4xx status`() {
    val expectedFileContent = getFileBytes(FILE_CONTENT)
    val subjectAccessRequestId = UUID.randomUUID()
    hmppsAuth.stubGrantToken()
    documentApi.stubUploadFileFailsWithStatus(
      subjectAccessRequestId = subjectAccessRequestId.toString(),
      expectedFileContent = FILE_CONTENT.toByteArray(),
      status = 400,
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
      "httpStatus" to HttpStatus.BAD_REQUEST,
    )

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
  }

  fun getFileBytes(input: String): ByteArrayOutputStream {
    val baos = ByteArrayOutputStream()
    IOUtils.copy(ByteArrayInputStream(input.toByteArray()), baos)
    return baos
  }
}
