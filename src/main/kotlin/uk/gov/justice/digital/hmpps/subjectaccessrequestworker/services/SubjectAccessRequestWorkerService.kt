package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.microsoft.applicationinsights.TelemetryClient
import io.sentry.Sentry
import kotlinx.coroutines.delay
import org.apache.commons.lang3.time.StopWatch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.DocumentStorageGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.PrisonApiGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.ProbationApiGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.SubjectAccessRequestGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.ConfigOrderHelper
import java.io.ByteArrayOutputStream
import java.util.UUID

const val POLL_DELAY: Long = 10000

@Service
class SubjectAccessRequestWorkerService(
  @Autowired val sarGateway: SubjectAccessRequestGateway,
  @Autowired val getSubjectAccessRequestDataService: GetSubjectAccessRequestDataService,
  @Autowired val documentStorageGateway: DocumentStorageGateway,
  @Autowired val generatePdfService: GeneratePdfService,
  @Autowired val prisonApiGateway: PrisonApiGateway,
  @Autowired val probationApiGateway: ProbationApiGateway,
  @Autowired val configOrderHelper: ConfigOrderHelper,
  @Value("\${services.sar-api.base-url}") private val sarUrl: String,
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    const val TIME_ELAPSED_KEY = "totalTimeElapsed"
  }

  suspend fun startPolling() {
    while (true) {
      log.info("Polling for reports...")
      doPoll()
    }
  }

  suspend fun doPoll() {
    var subjectAccessRequest: SubjectAccessRequest? = null
    val stopWatch: StopWatch = StopWatch.create()

    try {
      val webClient = sarGateway.getClient(sarUrl)
      subjectAccessRequest = pollForNewSubjectAccessRequests(webClient)

      claimSubjectAccessRequest(webClient, subjectAccessRequest)

      stopWatch.start()
      doReport(subjectAccessRequest)
      sarGateway.complete(webClient, subjectAccessRequest)
      stopWatch.stop()
      recordEvent("NewReportClaimComplete", subjectAccessRequest, TIME_ELAPSED_KEY to stopWatch.time.toString())
    } catch (exception: Exception) {
      log.error("subjectAccessRequest: ${subjectAccessRequest?.id} failed with error: ${exception.message}")

      recordEvent(
        "ReportFailedWithError",
        subjectAccessRequest,
        "error" to (exception.message ?: ""),
        TIME_ELAPSED_KEY to stopWatch.time.toString(),
      )

      exception.printStackTrace()
      Sentry.captureException(exception)
    }
  }

  suspend fun pollForNewSubjectAccessRequests(client: WebClient): SubjectAccessRequest {
    var response: Array<SubjectAccessRequest>? = emptyArray()

    while (response.isNullOrEmpty()) {
      log.info("polling in ${POLL_DELAY}ms")
      delay(POLL_DELAY)
      response = sarGateway.getUnclaimed(client)
    }
    return response.first()
  }

  suspend fun claimSubjectAccessRequest(webClient: WebClient, subjectAccessRequest: SubjectAccessRequest) {
    sarGateway.claim(webClient, subjectAccessRequest)
    log.info("report claimed with ID ${subjectAccessRequest.id} (case reference ${subjectAccessRequest.sarCaseReferenceNumber})")
    recordEvent("NewReportClaimStarted", subjectAccessRequest, TIME_ELAPSED_KEY to "0")
  }

  fun doReport(subjectAccessRequest: SubjectAccessRequest) {
    val stopWatch = StopWatch.createStarted()
    recordEvent("DoReportStarted", subjectAccessRequest, TIME_ELAPSED_KEY to "0")

    val selectedServices = getServiceDetails(subjectAccessRequest)

    log.info("${subjectAccessRequest.id} creating report..")
    recordEvent("CollectingServiceDataStarted", subjectAccessRequest, TIME_ELAPSED_KEY to stopWatch.time.toString())

    val dpsServiceList = getSubjectAccessRequestDataService.execute(
      selectedServices,
      subjectAccessRequest.nomisId,
      subjectAccessRequest.ndeliusCaseReferenceId,
      subjectAccessRequest.dateFrom,
      subjectAccessRequest.dateTo,
      subjectAccessRequest,
    )

    recordEvent("CollectingServiceDataComplete", subjectAccessRequest, TIME_ELAPSED_KEY to stopWatch.time.toString())

    log.info("${subjectAccessRequest.id} fetching subject name")
    recordEvent("CollectingSubjectNameStarted", subjectAccessRequest, TIME_ELAPSED_KEY to stopWatch.time.toString())

    var subjectName: String
    try {
      subjectName = getSubjectName(subjectAccessRequest.nomisId, subjectAccessRequest.ndeliusCaseReferenceId)
    } catch (exception: WebClientResponseException.NotFound) {
      subjectName = "No subject name found"
    }

    recordEvent("CollectingSubjectNameComplete", subjectAccessRequest, TIME_ELAPSED_KEY to stopWatch.time.toString())

    log.info("${subjectAccessRequest.id} extracted report")
    recordEvent("GeneratingPDFStreamStarted", subjectAccessRequest, TIME_ELAPSED_KEY to stopWatch.time.toString())

    val pdfStream = generatePdfService.execute(
      dpsServiceList,
      subjectAccessRequest.nomisId,
      subjectAccessRequest.ndeliusCaseReferenceId,
      subjectAccessRequest.sarCaseReferenceNumber,
      subjectName,
      subjectAccessRequest.dateFrom,
      subjectAccessRequest.dateTo,
      subjectAccessRequest,
    )
    log.info("${subjectAccessRequest.id} created PDF")

    recordEvent("GeneratingPDFStreamComplete", subjectAccessRequest, TIME_ELAPSED_KEY to stopWatch.time.toString())

    recordEvent("SavingFileStarted", subjectAccessRequest, TIME_ELAPSED_KEY to stopWatch.time.toString())

    val response = this.storeSubjectAccessRequestDocument(subjectAccessRequest.id, pdfStream)
    log.info("${subjectAccessRequest.id} stored PDF$response")

    recordEvent("SavingFileComplete", subjectAccessRequest, TIME_ELAPSED_KEY to stopWatch.time.toString())

    recordEvent("DoReportComplete", subjectAccessRequest, TIME_ELAPSED_KEY to stopWatch.time.toString())
  }

  fun storeSubjectAccessRequestDocument(sarId: UUID, docBody: ByteArrayOutputStream): String? {
    val response = documentStorageGateway.storeDocument(sarId, docBody)
    return response
  }

  fun getServicesMap(subjectAccessRequest: SubjectAccessRequest): MutableMap<String, String> {
    val services = subjectAccessRequest.services
    val serviceMap = mutableMapOf<String, String>()

    val serviceNames =
      services.split(',').map { splitService -> splitService.trim() }.filterIndexed { index, _ -> index % 2 == 0 }
    val serviceUrls =
      services.split(',').map { splitService -> splitService.trim() }.filterIndexed { index, _ -> index % 2 != 0 }

    for (serviceName in serviceNames) {
      serviceMap[serviceName] = serviceUrls[serviceNames.indexOf(serviceName)]
    }
    return serviceMap
  }

  fun getServiceDetails(
    subjectAccessRequest: SubjectAccessRequest,
  ): List<DpsService> {
    val servicesMap = getServicesMap(subjectAccessRequest)

    val selectedServices = configOrderHelper.getDpsServices(servicesMap)

    val serviceConfigObject = configOrderHelper.extractServicesConfig("servicesConfig.yaml")

    for (service in selectedServices) {
      if (serviceConfigObject != null) {
        for (configService in serviceConfigObject.dpsServices) {
          if (configService.name == service.name) {
            service.businessName = configService.businessName
            service.orderPosition = configService.orderPosition
          }
        }
      }
    }
    return selectedServices
  }

  fun getSubjectName(prisonId: String?, probationId: String?): String {
    if (prisonId !== null) {
      return prisonApiGateway.getOffenderName(prisonId)
    }
    if (probationId !== null) {
      return probationApiGateway.getOffenderName(probationId)
    }
    throw RuntimeException("Prison and Probation IDs are both null")
  }

  fun recordEvent(name: String, subjectAccessRequest: SubjectAccessRequest?, vararg kvpairs: Pair<String, String>) {
    val id = subjectAccessRequest?.sarCaseReferenceNumber ?: "unknown"
    telemetryClient.trackEvent(
      name,
      mapOf(
        "sarId" to id,
        "UUID" to subjectAccessRequest?.id.toString(),
        *kvpairs,
      ),
    )
  }
}
