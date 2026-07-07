package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.verify
import org.mockito.kotlin.times
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.codec.DecodingException
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.FILE_SIZE_VERIFY_FAILURE
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.FILE_SIZE_VERIFY_SUCCESS
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.STORE_DOCUMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestDocumentStoreConflictException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.DEFAULT_ERROR_CODE
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCodePrefix
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedSubjectAccessRequestExceptionWithCauseNull
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.fileHash
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiExtension.Companion.documentApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import wiremock.com.google.common.io.Files
import java.io.FileOutputStream
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID
import kotlin.io.path.fileSize

class DocumentStorageClientIntTest : BaseClientIntTest() {

  @TempDir
  lateinit var tempDirPath: Path

  @Autowired
  private lateinit var documentStorageClient: DocumentStorageClient

  @Autowired
  private lateinit var webClientRetriesSpec: WebClientRetriesSpec

  private val subjectAccessRequestId = UUID.randomUUID()
  private val contextId = UUID.randomUUID()
  private val subjectAccessRequest = SubjectAccessRequest(id = subjectAccessRequestId, contextId = contextId)
  private lateinit var pdfPath: Path
  private lateinit var pdfContent: ByteArray

  @MockitoBean
  protected lateinit var telemetryClient: TelemetryClient

  companion object {

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

    pdfPath = createTestPdf("Hello world!")
    pdfContent = getPdfFileBytes(pdfPath)

    hmppsAuth.stubGrantToken()
  }

  @Test
  fun `file upload success`() {
    documentApi.stubUploadFileSuccessWithMetadata(
      subjectAccessRequestId.toString(),
      pdfPath.fileSize().toInt(),
      pdfContent,
      1,
    )

    val resp = documentStorageClient.storeDocument(subjectAccessRequest, pdfPath)

    val expectedResponse = expectedSuccessResponse(content = pdfContent)
    assertThat(resp).isEqualTo(expectedResponse)

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
    verifyFileSizeVerifySuccessTelemetryEvent(subjectAccessRequest, pdfContent)
  }

  @ParameterizedTest
  @MethodSource("status4xxResponseStubs")
  fun `store document does not retry when fails with a 4xx status`(
    stubErrorResponse: BaseClientIntTest.Companion.StubErrorResponse,
  ) {
    documentApi.stubUploadFileFailsWithStatus(
      subjectAccessRequestId = subjectAccessRequestId.toString(),
      expectedFileContent = pdfContent,
      DEFAULT_ERROR_CODE,
      status = stubErrorResponse.status.value(),
    )

    val ex = assertThrows<FatalSubjectAccessRequestException> {
      documentStorageClient.storeDocument(subjectAccessRequest, pdfPath)
    }

    assertExpectedSubjectAccessRequestExceptionWithCauseNull(
      actual = ex,
      expectedPrefix = "subjectAccessRequest failed with non-retryable error: client 4xx response status",
      expectedEvent = STORE_DOCUMENT,
      expectedSubjectAccessRequest = subjectAccessRequest,
      expectedErrorCode = ErrorCode(
        ErrorCodePrefix.DOCUMENT_STORE,
        stubErrorResponse.status.value().toString(),
      ),
      expectedParams = mapOf(
        "uri" to "${documentApi.baseUrl()}/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId",
        "httpStatus" to stubErrorResponse.status,
      ),
    )

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
  }

  @Test
  fun `store document does not retry when fails with a 409 status`() {
    documentApi.stubUploadFileFailsWithStatus(
      subjectAccessRequestId = subjectAccessRequestId.toString(),
      expectedFileContent = pdfContent,
      errorCode = DEFAULT_ERROR_CODE,
      status = 409,
    )

    val ex = assertThrows<SubjectAccessRequestDocumentStoreConflictException> {
      documentStorageClient.storeDocument(subjectAccessRequest, pdfPath)
    }

    assertExpectedSubjectAccessRequestExceptionWithCauseNull(
      actual = ex,
      expectedPrefix = "subject access request document store upload unsuccessful: document already exists",
      expectedEvent = STORE_DOCUMENT,
      expectedErrorCode = ErrorCode.DOCUMENT_STORE_CONFLICT,
      expectedSubjectAccessRequest = subjectAccessRequest,
      expectedParams = mapOf(
        "uri" to "${documentApi.baseUrl()}/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId",
        "httpStatus" to HttpStatus.CONFLICT,
      ),
    )

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
  }

  @ParameterizedTest
  @MethodSource("status5xxResponseStubs")
  fun `store document retries on 5xx status`(stubErrorResponse: BaseClientIntTest.Companion.StubErrorResponse) {
    documentApi.stubUploadFileFailsWithStatus(
      subjectAccessRequestId.toString(),
      pdfContent,
      DEFAULT_ERROR_CODE,
      stubErrorResponse.status.value(),
    )

    val ex = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      documentStorageClient.storeDocument(subjectAccessRequest, pdfPath)
    }

    assertExpectedSubjectAccessRequestException(
      actual = ex,
      expectedPrefix = "subjectAccessRequest failed and max retry attempts (2) exhausted",
      expectedCause = stubErrorResponse.expectedException,
      expectedEvent = STORE_DOCUMENT,
      expectedErrorCode = ErrorCode(ErrorCodePrefix.DOCUMENT_STORE, DEFAULT_ERROR_CODE),
      expectedSubjectAccessRequest = subjectAccessRequest,
      expectedParams = mapOf(
        "uri" to "/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId",
      ),
    )

    documentApi.verifyStoreDocumentIsCalled(
      times = 3,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
  }

  @Test
  fun `store document is successful when initial request fails with 5xx status and first retry succeeds`() {
    hmppsAuth.stubGrantToken()

    documentApi.stubUploadFileFailsWithStatusThenSucceedsOnRetry(
      subjectAccessRequestId.toString(),
      pdfContent,
      500,
    )

    val resp = documentStorageClient.storeDocument(subjectAccessRequest, pdfPath)

    val expectedResponse = expectedSuccessResponse(content = pdfContent)
    assertThat(resp).isEqualTo(expectedResponse)

    documentApi.verifyStoreDocumentIsCalled(
      times = 2,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
  }

  @Test
  fun `store document retries on client request exception`() {
    val webClientWithIncorrectUrl = WebClient.builder()
      .baseUrl("http://localhost:${documentApi.port() + 10}")
      .build()

    documentStorageClient = DocumentStorageClient(webClientWithIncorrectUrl, webClientRetriesSpec, telemetryClient)

    val ex = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      documentStorageClient.storeDocument(subjectAccessRequest, pdfPath)
    }

    assertExpectedSubjectAccessRequestException(
      actual = ex,
      expectedPrefix = "subjectAccessRequest failed and max retry attempts (2) exhausted",
      expectedCause = WebClientRequestException::class.java,
      expectedEvent = STORE_DOCUMENT,
      expectedErrorCode = ErrorCode.defaultErrorCodeFor(ErrorCodePrefix.DOCUMENT_STORE),
      expectedSubjectAccessRequest = subjectAccessRequest,
      expectedParams = mapOf(
        "uri" to "/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId",
      ),
    )

    assertThat(ex.cause).isInstanceOf(WebClientRequestException::class.java)
    assertThat(ex.cause?.message).containsIgnoringCase("connection refused")

    documentApi.verifyNeverCalled()
  }

  @ParameterizedTest
  @MethodSource("authErrorResponseStubs")
  fun `documentStorageClient fails to obtain auth token with`(
    stubErrorResponse: BaseClientIntTest.Companion.StubErrorResponse,
  ) {
    hmppsAuth.stubGrantToken(stubErrorResponse.getResponse())

    val ex = assertThrows<FatalSubjectAccessRequestException> {
      documentStorageClient.storeDocument(subjectAccessRequest, pdfPath)
    }

    assertExpectedSubjectAccessRequestException(
      actual = ex,
      expectedPrefix = "subjectAccessRequest failed with non-retryable error: documentStoreClient error authorization exception",
      expectedCause = ClientAuthorizationException::class.java,
      expectedEvent = STORE_DOCUMENT,
      expectedErrorCode = ErrorCode.DOCUMENT_STORE_AUTH_ERROR,
      expectedSubjectAccessRequest = subjectAccessRequest,
    )

    hmppsAuth.verifyCalledOnce()
    documentApi.verifyNeverCalled()
  }

  @Test
  fun `uploaded document file size does not match expected content size throw exception`() {
    val incorrectFileSize = pdfPath.fileSize() * 2

    documentApi.stubUploadFileSuccessWithMetadata(
      subjectAccessRequestId.toString(),
      incorrectFileSize.toInt(),
      pdfContent,
      1,
    )

    val ex = assertThrows<SubjectAccessRequestException> {
      documentStorageClient.storeDocument(subjectAccessRequest, pdfPath)
    }

    assertExpectedSubjectAccessRequestExceptionWithCauseNull(
      actual = ex,
      expectedPrefix = "document store upload error: response file size did not match the expected file upload size",
      expectedEvent = STORE_DOCUMENT,
      expectedSubjectAccessRequest = subjectAccessRequest,
      expectedErrorCode = ErrorCode.DOCUMENT_UPLOAD_VERIFICATION_ERROR,
      expectedParams = mapOf(
        "expectedFileSize" to pdfPath.fileSize().toInt(),
        "actualFileSize" to incorrectFileSize.toInt(),
        "documentUuid" to subjectAccessRequestId.toString(),
        "documentFileHash" to fileHash(pdfContent),
      ),
    )

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )

    verifyFileSizeVerifyErrorTelemetryEvent(
      subjectAccessRequest,
      pdfContent,
      incorrectFileSize,
    )
  }

  @Test
  fun `document store upload success invalid response body throws expected exception`() {
    documentApi.stubUploadFileReturnsInvalidResponseEntity(
      subjectAccessRequestId.toString(),
      pdfContent,
    )

    val ex = assertThrows<SubjectAccessRequestException> {
      documentStorageClient.storeDocument(subjectAccessRequest, pdfPath)
    }

    assertExpectedSubjectAccessRequestException(
      actual = ex,
      expectedCause = DecodingException::class.java,
      expectedPrefix = "documentStoreClient unexpected error",
      expectedEvent = STORE_DOCUMENT,
      expectedErrorCode = ErrorCode.DOCUMENT_UPLOAD_VERIFICATION_ERROR,
      expectedSubjectAccessRequest = subjectAccessRequest,
      expectedParams = null,
    )

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
  }

  @ParameterizedTest()
  @MethodSource("metadataFormats")
  fun `store document will successfully handle response metadata of different value types`(metadata: Any?) {
    documentApi.stubUploadFileSuccessWithMetadata(
      subjectAccessRequestId = subjectAccessRequestId.toString(),
      fileSize = pdfPath.fileSize().toInt(),
      expectedFileContent = pdfContent,
      metadata = metadata,
    )

    val resp = documentStorageClient.storeDocument(subjectAccessRequest, pdfPath)

    val expectedResponse = expectedSuccessResponseWithMetadata(
      content = pdfContent,
      metadata = metadata,
    )
    assertThat(resp).isEqualTo(expectedResponse)

    documentApi.verifyStoreDocumentIsCalled(
      times = 1,
      subjectAccessRequestId = subjectAccessRequestId.toString(),
    )
    verifyFileSizeVerifySuccessTelemetryEvent(subjectAccessRequest, pdfContent)
  }

  fun createTestPdf(input: String): Path {
    val filepath = tempDirPath.resolve("report.pdf").toFile()
    FileOutputStream(filepath).use {
      PdfDocument(PdfWriter(it)).use { pdf ->
        Document(pdf).use { document ->
          document.add(Paragraph().add(input))
        }
      }
    }
    return filepath.toPath()
  }

  fun getPdfFileBytes(path: Path) = Files.toByteArray(path.toFile())

  fun expectedSuccessResponse(fileSize: Int? = null, content: ByteArray): DocumentStorageClient.PostDocumentResponse =
    objectMapper.readValue(
      documentApi.documentUploadSuccessResponseJson(
        subjectAccessRequestId.toString(),
        fileSize ?: content.size,
        content,
      ),
      DocumentStorageClient.PostDocumentResponse::class.java,
    )

  fun expectedSuccessResponseWithMetadata(
    fileSize: Int? = null,
    content: ByteArray,
    metadata: Any?,
  ): DocumentStorageClient.PostDocumentResponse = objectMapper.readValue(
    documentApi.documentUploadSuccessResponseJsonWithMetadata(
      subjectAccessRequestId.toString(),
      fileSize ?: content.size,
      content,
      metadata,
    ),
    DocumentStorageClient.PostDocumentResponse::class.java,
  )

  fun verifyFileSizeVerifySuccessTelemetryEvent(subjectAccessRequest: SubjectAccessRequest, content: ByteArray) {
    val props = mapOf(
      "sarId" to subjectAccessRequest.sarCaseReferenceNumber,
      "UUID" to subjectAccessRequest.id.toString(),
      "contextId" to contextId.toString(),
      "expectedFileSize" to content.size.toString(),
      "actualFileSize" to content.size.toString(),
      "documentUuid" to subjectAccessRequest.id.toString(),
      "documentFileHash" to fileHash(content),
    )

    verify(telemetryClient, times(1))
      .trackEvent(FILE_SIZE_VERIFY_SUCCESS.name, props, null)
  }

  fun verifyFileSizeVerifyErrorTelemetryEvent(
    subjectAccessRequest: SubjectAccessRequest,
    content: ByteArray,
    actualSize: Long,
  ) {
    val props = mapOf(
      "sarId" to subjectAccessRequest.sarCaseReferenceNumber,
      "UUID" to subjectAccessRequest.id.toString(),
      "expectedFileSize" to content.size.toString(),
      "actualFileSize" to actualSize.toString(),
      "documentUuid" to subjectAccessRequest.id.toString(),
      "documentFileHash" to fileHash(content),
      "contextId" to contextId.toString(),
    )

    verify(telemetryClient, times(1))
      .trackEvent(FILE_SIZE_VERIFY_FAILURE.name, props, null)
  }
}
