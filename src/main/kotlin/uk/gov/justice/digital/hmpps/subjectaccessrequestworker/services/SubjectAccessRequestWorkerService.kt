package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.microsoft.applicationinsights.TelemetryClient
import io.sentry.Sentry
import kotlinx.coroutines.delay
import org.apache.commons.lang3.time.StopWatch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
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

  private val log = LoggerFactory.getLogger(this::class.java)

  suspend fun startPolling() {
    while (true) {
      log.info("Polling for reports...")
      doPoll()
    }
  }

  suspend fun doPoll() {
    try {
      val webClient = sarGateway.getClient(sarUrl)
      val chosenSAR = this.pollForNewSubjectAccessRequests(webClient)
      val patchResponseCode = sarGateway.claim(webClient, chosenSAR)
      if (patchResponseCode == HttpStatusCode.valueOf(200)) {
        log.info("Report claimed with ID " + chosenSAR.id + " (Case Reference " + chosenSAR.sarCaseReferenceNumber + ")")
        telemetryClient.trackEvent(
          "NewReportClaimStarted",
          mapOf(
            "sarId" to chosenSAR.sarCaseReferenceNumber,
            "UUID" to chosenSAR.id.toString(),
            "totalTimeElapsed" to "0",
          ),
        )

        val stopWatch = StopWatch.createStarted()
        doReport(chosenSAR)
        sarGateway.complete(webClient, chosenSAR)
        stopWatch.stop()
        telemetryClient.trackEvent(
          "NewReportClaimComplete",
          mapOf(
            "sarId" to chosenSAR.sarCaseReferenceNumber,
            "UUID" to chosenSAR.id.toString(),
            "totalTimeElapsed" to stopWatch.time.toString(),
          ),
        )
      }
    } catch (exception: Exception) {
      log.error(exception.message)
      exception.printStackTrace()
      Sentry.captureException(exception)
    }
  }

  suspend fun pollForNewSubjectAccessRequests(client: WebClient): SubjectAccessRequest {
    var response: Array<SubjectAccessRequest>? = emptyArray()

    while (response.isNullOrEmpty()) {
      log.info("Polling in ${POLL_DELAY}ms")
      delay(POLL_DELAY)
      response = sarGateway.getUnclaimed(client)
    }
    return response.first()
  }

  fun doReport(chosenSAR: SubjectAccessRequest) {
    val stopWatch = StopWatch.createStarted()
    telemetryClient.trackEvent(
      "DoReportStarted",
      mapOf(
        "sarId" to chosenSAR.sarCaseReferenceNumber,
        "UUID" to chosenSAR.id.toString(),
      ),
    )
    val selectedServices = getServiceDetails(chosenSAR)

    log.info("Creating report..")
    telemetryClient.trackEvent(
      "CollectingServiceDataStarted",
      mapOf(
        "sarId" to chosenSAR.sarCaseReferenceNumber,
        "UUID" to chosenSAR.id.toString(),
        "totalTimeElapsed" to stopWatch.time.toString(),
      ),
    )

    val dpsServiceList = getSubjectAccessRequestDataService.execute(selectedServices, chosenSAR.nomisId, chosenSAR.ndeliusCaseReferenceId, chosenSAR.dateFrom, chosenSAR.dateTo, chosenSAR)

    telemetryClient.trackEvent(
      "CollectingServiceDataComplete",
      mapOf(
        "sarId" to chosenSAR.sarCaseReferenceNumber,
        "UUID" to chosenSAR.id.toString(),
        "totalTimeElapsed" to stopWatch.time.toString(),
      ),
    )

    log.info("Fetching subject name")
    telemetryClient.trackEvent(
      "CollectingSubjectNameStarted",
      mapOf(
        "sarId" to chosenSAR.sarCaseReferenceNumber,
        "UUID" to chosenSAR.id.toString(),
        "totalTimeElapsed" to stopWatch.time.toString(),
      ),
    )
    var subjectName: String
    try {
      subjectName = getSubjectName(chosenSAR.nomisId, chosenSAR.ndeliusCaseReferenceId)
    } catch (exception: WebClientResponseException.NotFound) {
      subjectName = "No subject name found"
    }
    telemetryClient.trackEvent(
      "CollectingSubjectNameComplete",
      mapOf(
        "sarId" to chosenSAR.sarCaseReferenceNumber,
        "UUID" to chosenSAR.id.toString(),
        "totalTimeElapsed" to stopWatch.time.toString(),
      ),
    )

    log.info("Extracted report")

    telemetryClient.trackEvent(
      "GeneratingPDFStreamStarted",
      mapOf(
        "sarId" to chosenSAR.sarCaseReferenceNumber,
        "UUID" to chosenSAR.id.toString(),
        "totalTimeElapsed" to stopWatch.time.toString(),
      ),
    )

    val pdfStream = generatePdfService.execute(dpsServiceList, chosenSAR.nomisId, chosenSAR.ndeliusCaseReferenceId, chosenSAR.sarCaseReferenceNumber, subjectName, chosenSAR.dateFrom, chosenSAR.dateTo, chosenSAR)
    log.info("Created PDF")

    telemetryClient.trackEvent(
      "GeneratingPDFStreamComplete",
      mapOf(
        "sarId" to chosenSAR.sarCaseReferenceNumber,
        "UUID" to chosenSAR.id.toString(),
        "totalTimeElapsed" to stopWatch.time.toString(),
      ),
    )

    telemetryClient.trackEvent(
      "SavingFileStarted",
      mapOf(
        "sarId" to chosenSAR.sarCaseReferenceNumber,
        "UUID" to chosenSAR.id.toString(),
        "totalTimeElapsed" to stopWatch.time.toString(),
      ),
    )

    val response = this.storeSubjectAccessRequestDocument(chosenSAR.id, pdfStream)
    log.info("Stored PDF$response")

    telemetryClient.trackEvent(
      "SavingFileComplete",
      mapOf(
        "sarId" to chosenSAR.sarCaseReferenceNumber,
        "UUID" to chosenSAR.id.toString(),
        "totalTimeElapsed" to stopWatch.time.toString(),
      ),
    )

    telemetryClient.trackEvent(
      "DoReportComplete",
      mapOf(
        "sarId" to chosenSAR.sarCaseReferenceNumber,
        "UUID" to chosenSAR.id.toString(),
        "totalTimeElapsed" to stopWatch.time.toString(),
      ),
    )
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
}
