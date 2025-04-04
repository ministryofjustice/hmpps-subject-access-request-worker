package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import com.fasterxml.jackson.annotation.JsonFormat
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.ACQUIRE_AUTH_TOKEN
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.HTML_RENDERER_REQUEST
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import java.time.LocalDate
import java.util.UUID

@Service
class HtmlRendererApiClient(
  private val sarHtmlRendererApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {

  fun submitRenderRequest(
    subjectAccessRequest: SubjectAccessRequest,
    serviceName: String,
    serviceUrl: String,
  ): HtmlRenderResponse? = try {
    sarHtmlRendererApiWebClient.post()
      .uri("/subject-access-request/render")
      .bodyValue(HtmlRenderRequest(subjectAccessRequest, serviceName, serviceUrl))
      .retrieve()
      .onStatus(
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(
          subjectAccessRequest = subjectAccessRequest,
          event = HTML_RENDERER_REQUEST,
          params = mapOf(
            "serviceName" to serviceName,
            "serviceUrl" to serviceUrl,
          ),
        ),
      )
      .bodyToMono(HtmlRenderResponse::class.java)
      .retryWhen(
        webClientRetriesSpec.retry5xxAndClientRequestErrors(
          subjectAccessRequest = subjectAccessRequest,
          event = HTML_RENDERER_REQUEST,
          params = mapOf(
            "serviceName" to serviceName,
            "serviceUrl" to serviceUrl,
          )
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
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
    val dateFrom: LocalDate?,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
    val dateTo: LocalDate?,
    val sarCaseReferenceNumber: String?,
    val serviceName: String,
    val serviceUrl: String,
  ) {
    constructor(subjectAccessRequest: SubjectAccessRequest, serviceName: String, serviceUrl: String) : this(
      id = subjectAccessRequest.id,
      nomisId = subjectAccessRequest.nomisId,
      ndeliusId = subjectAccessRequest.ndeliusCaseReferenceId,
      dateFrom = subjectAccessRequest.dateFrom,
      dateTo = subjectAccessRequest.dateTo,
      sarCaseReferenceNumber = subjectAccessRequest.sarCaseReferenceNumber,
      serviceName = serviceName,
      serviceUrl = serviceUrl,
    )
  }

  data class HtmlRenderResponse(val documentKey: String)
}
