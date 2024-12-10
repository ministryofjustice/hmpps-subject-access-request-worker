package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_SAR_DATA
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import java.time.LocalDate
import java.util.*

@Service
class DynamicServicesClient(
  @Qualifier("dynamicWebClient") private val dynamicApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {

  fun getDataFromService(
    serviceUrl: String,
    prn: String? = null,
    crn: String? = null,
    dateFrom: LocalDate? = null,
    dateTo: LocalDate? = null,
    subjectAccessRequest: SubjectAccessRequest? = null,
  ): ResponseEntity<Map<*, *>>? =
    dynamicApiWebClient.mutate().baseUrl(serviceUrl).build()
      .get()
      .uri {
        it.path("/subject-access-request")
          .queryParamIfPresent("prn", Optional.ofNullable(prn))
          .queryParamIfPresent("crn", Optional.ofNullable(crn))
          .queryParam("fromDate", dateFrom)
          .queryParam("toDate", dateTo)
          .build()
      }
      .retrieve()
      .onStatus(
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(
          GET_SAR_DATA,
          subjectAccessRequest,
        ),
      )
      .toEntity(Map::class.java)
      .retryWhen(
        webClientRetriesSpec.retry5xxAndClientRequestErrors(
          GET_SAR_DATA,
          subjectAccessRequest,
          mapOf(
            "uri" to serviceUrl,
          ),
        ),
      ).block()
}
