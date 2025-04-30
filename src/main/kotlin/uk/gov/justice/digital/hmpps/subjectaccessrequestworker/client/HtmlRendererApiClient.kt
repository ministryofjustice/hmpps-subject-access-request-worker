package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import com.fasterxml.jackson.annotation.JsonFormat
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.ACQUIRE_AUTH_TOKEN
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.HTML_RENDERER_REQUEST
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import java.time.LocalDate
import java.util.UUID

@Service
@ConditionalOnProperty(name = ["html-renderer.enabled"], havingValue = "true")
class HtmlRendererApiClient(
  private val sarHtmlRendererApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {

  fun submitRenderRequest(
    subjectAccessRequest: SubjectAccessRequest,
    service: DpsService,
  ): HtmlRenderResponse? = try {
    sarHtmlRendererApiWebClient.post()
      .uri("/subject-access-request/render")
      .bodyValue(HtmlRenderRequest(subjectAccessRequest, service))
      .retrieve()
      .onStatus(
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(
          subjectAccessRequest = subjectAccessRequest,
          event = HTML_RENDERER_REQUEST,
          params = mapOf(
            "serviceName" to service.name!!,
            "serviceUrl" to service.url!!,
          ),
        ),
      )
      .bodyToMono(HtmlRenderResponse::class.java)
      .retryWhen(
        webClientRetriesSpec.retry5xxAndClientRequestErrors(
          subjectAccessRequest = subjectAccessRequest,
          event = HTML_RENDERER_REQUEST,
          params = mapOf(
            "serviceName" to service.name,
            "serviceUrl" to service.url,
          ),
        ),
      )
      .block()
  } catch (ex: ClientAuthorizationException) {
    throw FatalSubjectAccessRequestException(
      message = "sarHtmlRendererApiClient error authorization exception",
      cause = ex,
      event = ACQUIRE_AUTH_TOKEN,
      params = mapOf(
        "cause" to ex.cause?.message,
      ),
    )
  }

  data class HtmlRenderRequest(
    val id: UUID,
    val nomisId: String?,
    val ndeliusId: String?,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val dateFrom: LocalDate?,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val dateTo: LocalDate?,
    val sarCaseReferenceNumber: String?,
    val serviceName: String,
    val serviceLabel: String,
    val serviceUrl: String,
  ) {
    constructor(subjectAccessRequest: SubjectAccessRequest, service: DpsService) : this(
      id = subjectAccessRequest.id,
      nomisId = subjectAccessRequest.nomisId,
      ndeliusId = subjectAccessRequest.ndeliusCaseReferenceId,
      dateFrom = subjectAccessRequest.dateFrom,
      dateTo = subjectAccessRequest.dateTo,
      sarCaseReferenceNumber = subjectAccessRequest.sarCaseReferenceNumber,
      serviceName = service.name!!,
      serviceLabel = service.businessName!!,
      serviceUrl = service.url!!,
    )
  }

  data class HtmlRenderResponse(val documentKey: String?)
}
