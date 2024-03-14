package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.microsoft.applicationinsights.TelemetryClient
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
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest
import java.io.ByteArrayOutputStream
import java.util.*

const val POLL_DELAY: Long = 10000

@Service
class SubjectAccessRequestWorkerService(
  @Autowired val sarGateway: SubjectAccessRequestGateway,
  @Autowired val getSubjectAccessRequestDataService: GetSubjectAccessRequestDataService,
  @Autowired val documentStorageGateway: DocumentStorageGateway,
  @Autowired val generatePdfService: GeneratePdfService,
  @Value("\${services.sar-api.base-url}")
  private val sarUrl: String,
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
    val webClient = sarGateway.getClient(sarUrl)
    val chosenSAR = this.pollForNewSubjectAccessRequests(webClient)
    val patchResponseCode = sarGateway.claim(webClient, chosenSAR)
    if (patchResponseCode == HttpStatusCode.valueOf(200)) {
      log.info("Report found!")
      try {
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
      } catch (exception: Exception) {
        log.error(exception.message)
        exception.printStackTrace()
      }
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
    log.info("Creating report..")
    val responseObject = getSubjectAccessRequestDataService.execute(chosenSAR.services, chosenSAR.nomisId, chosenSAR.ndeliusCaseReferenceId, chosenSAR.dateFrom, chosenSAR.dateTo)
    log.info("Extracted report data$responseObject")
    var nID = ""
    if (chosenSAR.nomisId != null) {
      nID = "NOMIS ID: ${chosenSAR.nomisId}"
    } else if (chosenSAR.ndeliusCaseReferenceId != null) {
      nID = "NDELIUS ID: ${chosenSAR.ndeliusCaseReferenceId}"
    }
    val pdfStream = generatePdfService.execute(responseObject, nID, chosenSAR.sarCaseReferenceNumber)
    log.info("Created PDF")
    val response = this.storeSubjectAccessRequestDocument(chosenSAR.id, pdfStream)
    log.info("Stored PDF$response")
  }

  fun storeSubjectAccessRequestDocument(sarId: UUID, docBody: ByteArrayOutputStream): String? {
    val response = documentStorageGateway.storeDocument(sarId, docBody)
    return response
  }
}
