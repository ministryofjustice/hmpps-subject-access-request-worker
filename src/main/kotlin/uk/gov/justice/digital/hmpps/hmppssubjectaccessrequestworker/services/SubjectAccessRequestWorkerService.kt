package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.DocumentStorageGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.SubjectAccessRequestGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest

const val POLL_DELAY: Long = 10000

@Service
class SubjectAccessRequestWorkerService(
  @Autowired val sarGateway: SubjectAccessRequestGateway,
  @Autowired val getSubjectAccessRequestDataService: GetSubjectAccessRequestDataService,
  @Autowired val documentStorageGateway: DocumentStorageGateway,
  @Value("\${services.sar-api.base-url}")
  private val sarUrl: String,
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
    val token = sarGateway.getClientTokenFromHmppsAuth()
    val chosenSAR = this.pollForNewSubjectAccessRequests(webClient, token)
    val patchResponseCode = sarGateway.claim(webClient, chosenSAR, token)
    if (patchResponseCode == HttpStatusCode.valueOf(200)) {
      log.info("Report found!")
      doReport(chosenSAR)
      sarGateway.complete(webClient, chosenSAR, token)
    }
  }

  suspend fun pollForNewSubjectAccessRequests(client: WebClient, token: String): SubjectAccessRequest {
    var response: Array<SubjectAccessRequest>? = emptyArray()

    while (response.isNullOrEmpty()) {
      log.info("Polling in ${POLL_DELAY}ms")
      delay(POLL_DELAY)
      response = sarGateway.getUnclaimed(client, token)
    }
    return response.first()
  }

  fun doReport(chosenSAR: SubjectAccessRequest) {
    try {
      getSubjectAccessRequestDataService.execute(chosenSAR.services, chosenSAR.nomisId, chosenSAR.ndeliusCaseReferenceId, chosenSAR.dateFrom, chosenSAR.dateTo)
      log.info("Would do report")
    } catch (exception: RuntimeException) {
      throw RuntimeException("Failed to retrieve data from upstream services.")
    }
  }

  fun storeSubjectAccessRequestDocument(sarId: Int, docBody: String): String {
    val idsForReference = documentStorageGateway.storeDocument(sarId, docBody)
    return idsForReference
  }
}
