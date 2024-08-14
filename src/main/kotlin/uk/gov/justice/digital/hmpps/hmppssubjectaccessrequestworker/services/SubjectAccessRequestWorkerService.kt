package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

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
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.config.trackEvent
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.DocumentStorageGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.PrisonApiGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.ProbationApiGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.SubjectAccessRequestGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils.ConfigOrderHelper
import java.io.ByteArrayOutputStream
import java.util.*

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
        val stopWatch = StopWatch.createStarted()
        doReport(chosenSAR)
        sarGateway.complete(webClient, chosenSAR)
        stopWatch.stop()
        telemetryClient.trackEvent(
          "NewReportGenerated",
          mapOf(
            "sarId" to chosenSAR.sarCaseReferenceNumber,
            "UUID" to chosenSAR.id.toString(),
            "time" to stopWatch.time.toString(),
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
    val selectedServices = getServiceDetails(chosenSAR)

    log.info("Creating report..")
    val dpsServiceList = getSubjectAccessRequestDataService.execute(selectedServices, chosenSAR.nomisId, chosenSAR.ndeliusCaseReferenceId, chosenSAR.dateFrom, chosenSAR.dateTo)

    log.info("Fetching subject name")
    val subjectName = getSubjectName(chosenSAR.nomisId, chosenSAR.ndeliusCaseReferenceId)

    log.info("Extracted report")

    val pdfStream = generatePdfService.execute(dpsServiceList, chosenSAR.nomisId, chosenSAR.ndeliusCaseReferenceId, chosenSAR.sarCaseReferenceNumber, subjectName, chosenSAR.dateFrom, chosenSAR.dateTo)
    log.info("Created PDF")

    val response = this.storeSubjectAccessRequestDocument(chosenSAR.id, pdfStream)
    log.info("Stored PDF$response")
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
