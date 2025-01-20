package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact.v1

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactDslJsonBody
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt
import au.com.dius.pact.core.model.V4Pact
import com.nimbusds.jose.shaded.gson.GsonBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.TemplateRenderService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

typealias SarResponseEntity = Map<*, *>?

const val PRN = "A1234BC"

@ExtendWith(PactConsumerTestExt::class)
class BasePactTest : IntegrationTestBase() {

  @Autowired
  protected lateinit var dynamicServicesClient: DynamicServicesClient

  @Autowired
  protected lateinit var templateRenderService: TemplateRenderService

  @MockitoBean
  protected lateinit var userDetailsRepository: UserDetailsRepository

  protected val FROM_DATE = "01/01/2024"
  protected val TO_DATE = "01/01/2025"
  protected val gson = GsonBuilder().setPrettyPrinting().create()

  protected fun createPact(
    pactScenario: String,
    builder: PactDslWithProvider,
    prn: String,
    fromDate: String,
    toDate: String,
    responseBody: PactDslJsonBody,
  ): V4Pact {
    return builder
      .given(pactScenario)
      .uponReceiving("a subject access request")
      .path("/subject-access-request")
      .matchQuery("prn", prn)
      .queryMatchingDate("fromDate", fromDate)
      .queryMatchingDate("toDate", toDate)
      .method("GET")
      .willRespondWith()
      .status(200)
      .headers(mapOf("Content-Type" to "application/json"))
      .body(responseBody)
      .toPact(V4Pact::class.java)
  }

  fun <T> SarResponseEntity.toModel(t: Class<T>): T {
    val result = gson.fromJson(gson.toJson(this), t)
    assertThat(result).isNotNull
    return result
  }

  protected fun getSubjectAccessRequestServiceData(mockServer: MockServer): SarResponseEntity {
    hmppsAuth.stubGrantToken()

    val resp = dynamicServicesClient.getDataFromService(
      serviceUrl = mockServer.getUrl(),
      prn = PRN,
      crn = null,
      dateFrom = LocalDate.parse(FROM_DATE, DateTimeFormatter.ofPattern("dd/MM/yyyy")),
      dateTo = LocalDate.parse(TO_DATE, DateTimeFormatter.ofPattern("dd/MM/yyyy")),
      SubjectAccessRequest(id = UUID.randomUUID()),
    )
    return resp?.body
  }

}