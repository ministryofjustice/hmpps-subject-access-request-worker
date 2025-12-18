package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.GotenbergApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCodePrefix
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.REFERENCE_PDF_BASE_DIR
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedSubjectAccessRequestExceptionWithCauseNull
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client.BaseClientIntTest.Companion.StubErrorResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.GotenbergApiExtension.Companion.gotenbergApi

private const val CONVERTED_FILENAME = "word-doc-A4.pdf"
private const val FILE_TO_CONVERT = "doc.docx"

class GotenbergApiClientIntTest : BaseClientIntTest() {

  @Autowired
  private lateinit var gotenbergApiClient: GotenbergApiClient

  @Test
  fun `should get pdf from word doc successfully`() {
    val pdfFileBytes = this::class.java.getResourceAsStream("$REFERENCE_PDF_BASE_DIR/attachments/$CONVERTED_FILENAME")!!.readAllBytes()
    gotenbergApi.stubOfficeConvert(pdfFileBytes)

    val response = gotenbergApiClient.convertWordDocToPdf(ByteArray(0), FILE_TO_CONVERT)

    assertThat(response).isEqualTo(pdfFileBytes)
  }

  @ParameterizedTest
  @MethodSource("status4xxResponseStubs")
  fun `should not retry on 4xx error`(stubResponse: StubErrorResponse) {
    gotenbergApi.stubOfficeConvertResponse(stubResponse.getResponse())

    val exception = assertThrows<FatalSubjectAccessRequestException> {
      gotenbergApiClient.convertWordDocToPdf(ByteArray(0), FILE_TO_CONVERT)
    }

    gotenbergApi.verifyOfficeConvertCalled(1, FILE_TO_CONVERT)

    assertExpectedSubjectAccessRequestExceptionWithCauseNull(
      actual = exception,
      expectedPrefix = "subjectAccessRequest failed with non-retryable error: client 4xx response status",
      expectedEvent = ProcessingEvent.CONVERT_WORD_DOCUMENT,
      expectedErrorCode = ErrorCode(ErrorCodePrefix.GOTENBERG_API, stubResponse.status.value().toString()),
      expectedParams = mapOf(
        "filename" to FILE_TO_CONVERT,
        "uri" to "${gotenbergApi.baseUrl()}/forms/libreoffice/convert",
        "httpStatus" to stubResponse.status,
      ),
    )
  }

  @ParameterizedTest
  @MethodSource("status5xxResponseStubs")
  fun `should retry on 5xx error`(stubResponse: StubErrorResponse) {
    gotenbergApi.stubOfficeConvertResponse(stubResponse.getResponse())

    val exception = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      gotenbergApiClient.convertWordDocToPdf(ByteArray(0), FILE_TO_CONVERT)
    }

    gotenbergApi.verifyOfficeConvertCalled(3, FILE_TO_CONVERT)

    assertExpectedSubjectAccessRequestException(
      actual = exception,
      expectedPrefix = "subjectAccessRequest failed and max retry attempts (2) exhausted",
      expectedCause = stubResponse.expectedException,
      expectedEvent = ProcessingEvent.CONVERT_WORD_DOCUMENT,
      expectedErrorCode = ErrorCode(ErrorCodePrefix.GOTENBERG_API, stubResponse.status.value().toString()),
      expectedParams = mapOf(
        "filename" to FILE_TO_CONVERT,
      ),
    )
  }
}
