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
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.SubjectAccessRequestGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsServices
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
    val chosenSarServiceMap = getServicesMap(chosenSAR)

    log.info("Creating report..")

    val responseObject = getSubjectAccessRequestDataService.execute(chosenSarServiceMap, chosenSAR.nomisId, chosenSAR.ndeliusCaseReferenceId, chosenSAR.dateFrom, chosenSAR.dateTo)
    log.info("Extracted report")

    val pdfStream = generatePdfService.execute(responseObject, chosenSAR.nomisId, chosenSAR.ndeliusCaseReferenceId, chosenSAR.sarCaseReferenceNumber, chosenSAR.dateFrom, chosenSAR.dateTo, chosenSarServiceMap)
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
    var serviceMap = mutableMapOf<String, String>()

    val serviceNames =
      services.split(',').map { splitService -> splitService.trim() }.filterIndexed { index, _ -> index % 2 == 0 }
    val serviceUrls =
      services.split(',').map { splitService -> splitService.trim() }.filterIndexed { index, _ -> index % 2 != 0 }

    for (serviceName in serviceNames) {
      serviceMap.put(serviceName, serviceUrls[serviceNames.indexOf(serviceName)])
    }
    return serviceMap
  }

  fun getServiceDetails(
    subjectAccessRequest: SubjectAccessRequest
  ): DpsServices {
    val servicesMap = getServicesMap(subjectAccessRequest)

    val dpsServicesObject = DpsServices()

    servicesMap.forEach { (key, value) ->
      dpsServicesObject.dpsServices.add(DpsService(url = value, name = key))
    }

    val config = configOrderHelper.extractServicesConfig("servicesConfig.yaml")

    for (service in dpsServicesObject.dpsServices) {
      if (config != null) {
        for (configService in config.dpsServices) {
          if (configService.name == service.name) {
            service.businessName = configService.businessName
            service.orderPosition = configService.orderPosition
          }
        }
      }
    }
    return dpsServicesObject
  }
}
