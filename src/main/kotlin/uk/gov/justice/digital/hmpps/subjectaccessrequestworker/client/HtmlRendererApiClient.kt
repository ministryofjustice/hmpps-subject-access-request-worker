package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import com.fasterxml.jackson.annotation.JsonFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.ACQUIRE_AUTH_TOKEN
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.HTML_RENDERER_REQUEST
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.HTML_RENDERER_AUTH_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCodePrefix
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
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
    serviceConfiguration: ServiceConfiguration,
  ): HtmlRenderResponse? = try {
    sarHtmlRendererApiWebClient.post()
      .uri("/subject-access-request/render")
      .bodyValue(
        HtmlRenderRequest(
          subjectAccessRequest,
          serviceConfiguration.id,
        ),
      )
      .retrieve()
      .onStatus(
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(
          subjectAccessRequest = subjectAccessRequest,
          event = HTML_RENDERER_REQUEST,
          errorCodePrefix = ErrorCodePrefix.SAR_HTML_RENDERER,
          params = mapOf(
            "serviceName" to serviceConfiguration.serviceName,
            "serviceUrl" to serviceConfiguration.url,
          ),
        ),
      )
      .bodyToMono(HtmlRenderResponse::class.java)
      .retryWhen(
        webClientRetriesSpec.retryHtmlRenderer5xxAndClientRequestErrors(
          subjectAccessRequest,
          serviceConfiguration,
        ),
      )
      .block()
  } catch (ex: ClientAuthorizationException) {
    throw FatalSubjectAccessRequestException(
      message = "sarHtmlRendererApiClient error authorization exception",
      cause = ex,
      event = ACQUIRE_AUTH_TOKEN,
      errorCode = HTML_RENDERER_AUTH_ERROR,
      params = mapOf(
        "cause" to ex.cause?.message,
      ),
    )
  }

  fun getServiceSummary(
    subjectDataHeldRequest: SubjectDataHeldRequest,
  ): ResponseEntity<SubjectDataHeldResponse>? = sarHtmlRendererApiWebClient
    .post()
    .uri("/subject-access-request/subject-data-held-summary")
    .bodyValue(subjectDataHeldRequest)
    .retrieve()
    .toEntity(SubjectDataHeldResponse::class.java)
    .block()

  data class SubjectDataHeldRequest(
    val nomisId: String? = null,
    val ndeliusId: String? = null,
    @param:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val dateFrom: LocalDate? = null,
    @param:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
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

  data class HtmlRenderRequest(
    val id: UUID,
    val nomisId: String?,
    val ndeliusId: String?,
    @param:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val dateFrom: LocalDate?,
    @param:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val dateTo: LocalDate?,
    val sarCaseReferenceNumber: String?,
    val serviceConfigurationId: UUID,
  ) {
    constructor(subjectAccessRequest: SubjectAccessRequest, serviceConfigurationId: UUID) : this(
      id = subjectAccessRequest.id,
      nomisId = subjectAccessRequest.nomisId,
      ndeliusId = subjectAccessRequest.ndeliusCaseReferenceId,
      dateFrom = subjectAccessRequest.dateFrom,
      dateTo = subjectAccessRequest.dateTo,
      sarCaseReferenceNumber = subjectAccessRequest.sarCaseReferenceNumber,
      serviceConfigurationId = serviceConfigurationId,
    )
  }

  data class HtmlRenderResponse(val documentKey: String?)
}
