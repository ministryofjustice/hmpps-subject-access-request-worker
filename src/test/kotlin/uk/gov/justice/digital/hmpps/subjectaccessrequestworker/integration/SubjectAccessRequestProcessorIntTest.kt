package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.alerting.AlertsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.createSubjectAccessRequestForService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.expectedSubjectAccessRequestParameters
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.testNomisId
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.toGetParams
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.TestCase
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiExtension.Companion.documentApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ServiceOneApiExtension.Companion.serviceOneMockApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.LocationDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.scheduled.SubjectAccessRequestProcessor
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.testutils.TemplateTestingUtil
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

@TestPropertySource(
  properties = [
    "G1-api.url=http://localhost:4100",
    "G2-api.url=http://localhost:4100",
    "G3-api.url=http://localhost:4100",
  ],
)
class SubjectAccessRequestProcessorIntTest : IntegrationTestBase() {

  @MockitoSpyBean
  private lateinit var subjectAccessRequestProcessor: SubjectAccessRequestProcessor

  @MockitoBean
  private lateinit var alertsService: AlertsService

  @BeforeEach
  fun setup() {
    /**
     * Ensure the test generated reports have the same 'report generation date' as pre-generated reference reports.
     */
    whenever(dateService.now()).thenReturn(TemplateTestingUtil.reportGenerationDate)

    // Remove the cache client token to force each test to obtain an Auth token before calling the documentStore API.
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")

    subjectAccessRequestRepository.deleteAll()

    prisonDetailsRepository.deleteAll()
    prisonDetailsRepository.saveAndFlush(PrisonDetail("MDI", "MOORLAND (HMP & YOI)"))
    prisonDetailsRepository.saveAndFlush(PrisonDetail("LEI", "LEEDS (HMP)"))

    locationDetailsRepository.deleteAll()
    locationDetailsRepository.saveAndFlush(
      LocationDetail(
        "cac85758-380b-49fc-997f-94147e2553ac",
        357591,
        "ASSO A WING",
      ),
    )
    locationDetailsRepository.saveAndFlush(
      LocationDetail(
        "d0763236-c073-4ef4-9592-419bf0cd72cb",
        357592,
        "ASSO B WING",
      ),
    )
    locationDetailsRepository.saveAndFlush(LocationDetail("8ac39ebb-499d-4862-ae45-0b091253e89d", 27187, "ADJ"))
  }

  @AfterEach
  fun cleanup() {
    subjectAccessRequestRepository.deleteAll()
    subjectAccessRequestRepository.deleteAll()
    prisonDetailsRepository.deleteAll()
    locationDetailsRepository.deleteAll()

    // Remove the cache client token to force each test to obtain an Auth token before calling the documentStore API.
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")
  }

  @Test
  fun `should process pending request successfully when data is held`() {
    val serviceName = "keyworker-api"
    val subjectAccessRequest = createSubjectAccessRequestWithStatus(Status.Pending, serviceName)

    hmppsAuthGrantsToken()
    hmppsServiceReturnsSarData(serviceName = serviceName, subjectAccessRequest = subjectAccessRequest)
    prisonApiReturnsOffenderDetailsForNomisId(nomisId = subjectAccessRequest.nomisId!!)
    documentUploadIsSuccessful(subjectAccessRequest = subjectAccessRequest)

    await()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted {
        verify(subjectAccessRequestProcessor, atLeastOnce()).execute()

        verifyHmppsAuthIsCalledOnce()
        verifyHmppsSarEndpointCalledOnce()
        verifyDocumentStorageApiCalledOnce(id = subjectAccessRequest.id)
        assertUploadedDocumentMatchesExpectedPdf(serviceName = serviceName)
        assertSubjectAccessRequestStatusIsComplete(subjectAccessRequest = subjectAccessRequest)
        verifyNoInteractions(alertsService)
        verifyNoInteractions(alertsService)
      }
  }

  @Disabled
  @ParameterizedTest
  @MethodSource("uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture#generateReportTestCases")
  fun `should process pending request successfully when no data is held`(testCase: TestCase) {
    val subjectAccessRequest = createSubjectAccessRequestWithStatus(Status.Pending, testCase.serviceName)

    hmppsAuthGrantsToken()
    hmppsServiceReturnsNoSarData()
    prisonApiReturnsOffenderDetailsForNomisId(nomisId = subjectAccessRequest.nomisId!!)
    documentUploadIsSuccessful(subjectAccessRequest = subjectAccessRequest)

    await()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted {
        verify(subjectAccessRequestProcessor, atLeastOnce()).execute()
        verifyHmppsAuthIsCalledOnce()
        verifyHmppsSarEndpointCalledOnce()
        verifyDocumentStorageApiCalledOnce(id = subjectAccessRequest.id)
        assertUploadedDocumentMatchesExpectedNoDataHeldPdf(testCase = testCase)
        assertSubjectAccessRequestStatusIsComplete(subjectAccessRequest = subjectAccessRequest)
        verifyNoInteractions(alertsService)
      }
  }

  private fun createSubjectAccessRequestWithStatus(status: Status, serviceName: String): SubjectAccessRequest {
    val sar = createSubjectAccessRequestForService(serviceName, status)
    subjectAccessRequestRepository.saveAndFlush(sar)

    val pendingRequest = subjectAccessRequestRepository.findById(sar.id)
    assertThat(pendingRequest.getOrNull()).isNotNull
    assertThat(pendingRequest.get().status).isEqualTo(Status.Pending)

    return sar
  }

  private fun hmppsAuthGrantsToken() = hmppsAuth.stubGrantToken()

  private fun hmppsServiceReturnsSarData(serviceName: String, subjectAccessRequest: SubjectAccessRequest) {
    val responseBody = getSarResponseStub("$serviceName-stub.json")

    serviceOneMockApi.stubResponseFor(
      response = ResponseDefinitionBuilder()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(responseBody),
      params = subjectAccessRequest.toGetParams(),
    )
  }

  fun hmppsServiceReturnsNoSarData() {
    serviceOneMockApi.stubResponseFor(
      ResponseDefinitionBuilder()
        .withStatus(204)
        .withHeader("Content-Type", "application/json"),
      expectedSubjectAccessRequestParameters,
    )
  }

  private fun documentUploadIsSuccessful(subjectAccessRequest: SubjectAccessRequest) = documentApi
    .stubUploadFileSuccess(subjectAccessRequest.id.toString())

  fun prisonApiReturnsOffenderDetailsForNomisId(nomisId: String) = prisonApi.stubGetOffenderDetails(nomisId)

  fun assertUploadedDocumentMatchesExpectedPdf(serviceName: String) {
    val expected = getPreGeneratedPdfDocument("$serviceName-reference.pdf")
    val actual = getUploadedPdfDocument()

    assertThat(actual.numberOfPages).isEqualTo(expected.numberOfPages)

    for (i in 1..actual.numberOfPages) {
      val actualPageN = PdfTextExtractor.getTextFromPage(actual.getPage(i), SimpleTextExtractionStrategy())
      val expectedPageN = PdfTextExtractor.getTextFromPage(expected.getPage(i), SimpleTextExtractionStrategy())

      assertThat(actualPageN)
        .isEqualTo(expectedPageN)
        .withFailMessage("actual page: $i did not match expected.")
    }
  }

  fun assertSubjectAccessRequestStatusIsComplete(subjectAccessRequest: SubjectAccessRequest) {
    val target = subjectAccessRequestRepository.findById(subjectAccessRequest.id)
    assertThat(target.getOrNull()).isNotNull
    assertThat(target.get().status).isEqualTo(Status.Completed)
  }

  fun assertUploadedDocumentMatchesExpectedNoDataHeldPdf(testCase: TestCase) {
    val actual = getUploadedPdfDocument()

    assertThat(actual.numberOfPages).isEqualTo(5)
    val actualPageContent = PdfTextExtractor.getTextFromPage(actual.getPage(4), SimpleTextExtractionStrategy())
    val expectedPageContent = contentWhenNoDataHeld(testCase.serviceLabel, testNomisId)

    assertThat(actualPageContent)
      .isEqualTo(expectedPageContent)
      .withFailMessage("${testCase.serviceName} report did not match expected")
  }

  private fun contentWhenNoDataHeld(serviceLabel: String, nomidId: String): String = StringBuilder("$serviceLabel ")
    .append("\n")
    .append("No Data Held")
    .append("\n")
    .append("Name: REACHER, Joe ")
    .append("\n")
    .append("NOMIS ID: $nomidId")
    .append("\n")
    .append("Official Sensitive")
    .toString()
}
