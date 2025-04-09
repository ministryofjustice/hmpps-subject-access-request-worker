package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.createSubjectAccessRequestForService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiExtension.Companion.documentApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HtmlRendererApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.LocationsApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.LocationsApiExtension.Companion.locationsApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.NomisMappingsApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.NomisMappingsApiExtension.Companion.nomisMappingsApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.PrisonApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ProbationApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ProbationApiExtension.Companion.probationApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ServiceOneApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ServiceTwoApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.LocationDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.SubjectAccessRequestRepository
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

const val REFERENCE_PDF_BASE_DIR = "/integration-tests/reference-pdfs"
const val SAR_STUB_RESPONSES_DIR = "/integration-tests/api-response-stubs"

@ExtendWith(
  HmppsAuthApiExtension::class,
  DocumentApiExtension::class,
  PrisonApiExtension::class,
  ProbationApiExtension::class,
  ServiceOneApiExtension::class,
  ServiceTwoApiExtension::class,
  LocationsApiExtension::class,
  NomisMappingsApiExtension::class,
  HtmlRendererApiExtension::class,
)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
abstract class IntegrationTestBase {

  @Autowired
  protected lateinit var subjectAccessRequestRepository: SubjectAccessRequestRepository

  @Autowired
  protected lateinit var prisonDetailsRepository: PrisonDetailsRepository

  @Autowired
  protected lateinit var locationDetailsRepository: LocationDetailsRepository

  @Autowired
  protected lateinit var oAuth2AuthorizedClientService: OAuth2AuthorizedClientService

  @Autowired
  protected lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

  internal fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  protected fun stubPingWithResponse(status: Int) {
    hmppsAuth.stubHealthPing(status)
    prisonApi.stubHealthPing(status)
    probationApi.stubHealthPing(status)
    documentApi.stubHealthPing(status)
    locationsApi.stubHealthPing(status)
    nomisMappingsApi.stubHealthPing(status)
  }

  protected fun clearOauthClientCache(clientId: String, principalName: String) = oAuth2AuthorizedClientService
    .removeAuthorizedClient(clientId, principalName)

  protected fun getSarResponseStub(filename: String): String = this::class.java
    .getResourceAsStream("$SAR_STUB_RESPONSES_DIR/$filename").use { input ->
      InputStreamReader(input).readText()
    }

  protected fun getPreGeneratedPdfDocument(expectedPdfFilename: String): PdfDocument {
    val inputStream = this::class.java.getResourceAsStream("$REFERENCE_PDF_BASE_DIR/$expectedPdfFilename")
    assertThat(inputStream).isNotNull
    return pdfDocumentFromInputStream(inputStream!!)
  }

  protected fun pdfDocumentFromInputStream(inputStream: InputStream): PdfDocument = PdfDocument(PdfReader(inputStream))

  protected fun getUploadedPdfDocument(): PdfDocument = pdfDocumentFromInputStream(
    ByteArrayInputStream(documentApi.getRequestBodyAsByteArray()),
  )

  protected fun createSubjectAccessRequestWithStatus(status: Status, serviceName: String): SubjectAccessRequest {
    val sar = createSubjectAccessRequestForService(serviceName, status)
    return subjectAccessRequestRepository.saveAndFlush(sar)
  }

  protected fun assertSubjectAccessRequestHasStatus(subjectAccessRequest: SubjectAccessRequest, status: Status) {
    assertThat(getSubjectAccessRequest(subjectAccessRequest.id).status).isEqualTo(status)
  }

  protected fun getSubjectAccessRequest(id: UUID): SubjectAccessRequest {
    val optional = subjectAccessRequestRepository.findById(id)
    assertThat(optional.isPresent).isTrue()
    return optional.get()
  }

  protected fun rendererSuccessResponse(
    documentKey: String,
  ): ResponseDefinitionBuilder = ResponseDefinitionBuilder.responseDefinition()
    .withStatus(201)
    .withHeader("Content-Type", "application/json")
    .withBody("""{ "documentKey": "$documentKey" }""".trimIndent())

  protected fun rendererErrorResponse(
    status: HttpStatus,
  ): ResponseDefinitionBuilder = ResponseDefinitionBuilder.responseDefinition()
    .withStatus(status.value())
    .withHeader("Content-Type", "application/json")
    .withBody(status.reasonPhrase)
}
