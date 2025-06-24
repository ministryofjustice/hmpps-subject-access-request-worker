package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import com.fasterxml.jackson.annotation.JsonFormat
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_SAR_DATA
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_SUBJECT_DATA_HELD_SUMMARY
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import java.time.LocalDate
import java.util.Optional

@Service
class DynamicServicesClient(
  @Qualifier("dynamicWebClient") private val dynamicApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
  @Value("\${sar-html-renderer-api.url}") val sarHtmlRendererApiBaseUri: String,
) {

  fun getDataFromService(
    serviceUrl: String,
    prn: String? = null,
    crn: String? = null,
    dateFrom: LocalDate? = null,
    dateTo: LocalDate? = null,
    subjectAccessRequest: SubjectAccessRequest? = null,
  ): ResponseEntity<Map<*, *>>? = dynamicApiWebClient.mutate().baseUrl(serviceUrl).build()
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

  fun getServiceSummary(
    subjectDataHeldRequest: SubjectDataHeldRequest,
  ): ResponseEntity<SubjectDataHeldResponse>? = dynamicApiWebClient
    .mutate().baseUrl(sarHtmlRendererApiBaseUri).build()
    .post()
    .uri("/subject-access-request/subject-data-held-summary")
    .bodyValue(subjectDataHeldRequest)
    .retrieve()
    .toEntity(SubjectDataHeldResponse::class.java)
    .block()

  data class SubjectDataHeldRequest(
    val nomisId: String? = null,
    val ndeliusId: String? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val dateFrom: LocalDate? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val dateTo: LocalDate? = null,
    val serviceName: String? = null,
    val serviceUrl: String? = null,
  ) {
    constructor() : this("", "", null, null, null)
  }

  data class SubjectDataHeldResponse(
    val nomisId: String?,
    val ndeliusId: String?,
    val dataHeld: Boolean,
    val serviceName: String?,
  )
}
