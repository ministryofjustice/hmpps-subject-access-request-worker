package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.time.StopWatch
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDate

@Service
class GetSubjectAccessRequestDataService(
  val dynamicServicesClient: DynamicServicesClient,
  val telemetryClient: TelemetryClient,
) {

  fun requestDataFromServices(
    services: List<DpsService>,
    nomisId: String? = null,
    ndeliusId: String? = null,
    dateFrom: LocalDate? = null,
    dateTo: LocalDate? = null,
    subjectAccessRequest: SubjectAccessRequest? = null,
  ): List<DpsService> {
    val orderedServices = this.order(services)

    orderedServices.forEach {
      val response: Map<*, *>? = getSubjectAccessRequestDataFromServices(
        it.url!!,
        nomisId,
        ndeliusId,
        dateFrom,
        dateTo,
        subjectAccessRequest,
      )
      if (response != null && response.containsKey("content")) {
        it.content = response["content"]
      } else {
        it.content = "No Data Held"
      }
    }
    return orderedServices
  }

  fun order(services: List<DpsService>): List<DpsService> {
    val servicesWithNoOrderPosition = mutableListOf<DpsService>()
    val servicesWithOrderPosition = mutableListOf<DpsService>()

    services.forEach { service ->
      if (service.orderPosition != null) {
        servicesWithOrderPosition.add(service)
      } else {
        servicesWithNoOrderPosition.add(service)
      }
    }

    val servicesSortedByOrderPosition = servicesWithOrderPosition.sortedBy { it.orderPosition }
    val servicesWithNoOrderPositionSortedByName = servicesWithNoOrderPosition.sortedBy { it.name }

    return servicesSortedByOrderPosition + servicesWithNoOrderPositionSortedByName
  }

  private fun getSubjectAccessRequestDataFromServices(
    serviceUrl: String,
    prn: String? = null,
    crn: String? = null,
    dateFrom: LocalDate? = null,
    dateTo: LocalDate? = null,
    subjectAccessRequest: SubjectAccessRequest? = null,
  ): Map<*, *>? {
    val stopWatch = StopWatch.createStarted()
    telemetryClient.dataRequestStarted(subjectAccessRequest, serviceUrl)

    val responseEntity = try {
      dynamicServicesClient.getDataFromService(serviceUrl, prn, crn, dateFrom, dateTo, subjectAccessRequest)
    } catch (ex: Exception) {
      telemetryClient.dataRequestException(subjectAccessRequest, serviceUrl, stopWatch, ex)
      throw ex
    }
    stopWatch.stop()

    val responseStatus = responseEntity?.statusCode?.value()?.toString() ?: "Unknown"
    val responseBody = responseEntity?.body

    if (responseBody != null) {
      telemetryClient.dataRequestComplete(subjectAccessRequest, serviceUrl, stopWatch, responseBody, responseStatus)
    } else {
      telemetryClient.dataRequestCompleteNoDate(subjectAccessRequest, serviceUrl, stopWatch, responseStatus)
    }
    return responseBody
  }

  fun TelemetryClient.dataRequestStarted(subjectAccessRequest: SubjectAccessRequest?, serviceUrl: String) {
    telemetryClient.trackSarEvent(
      "ServiceDataRequestStarted",
      subjectAccessRequest,
      "serviceURL" to serviceUrl,
    )
  }

  fun TelemetryClient.dataRequestException(
    subjectAccessRequest: SubjectAccessRequest?,
    serviceUrl: String,
    stopWatch: StopWatch,
    ex: Exception,
  ) {
    telemetryClient.trackSarEvent(
      "ServiceDataRequestException",
      subjectAccessRequest,
      "serviceURL" to serviceUrl,
      "eventTime" to stopWatch.nanoTime.toString(),
      "responseSize" to "0",
      "responseStatus" to "Exception",
      "errorMessage" to (ex.message ?: "unknown"),
    )
  }

  fun TelemetryClient.dataRequestComplete(
    subjectAccessRequest: SubjectAccessRequest?,
    serviceUrl: String,
    stopWatch: StopWatch,
    responseBody: Map<*, *>,
    responseStatus: String,
  ) {
    telemetryClient.trackSarEvent(
      "ServiceDataRequestComplete",
      subjectAccessRequest,
      "serviceURL" to serviceUrl,
      "eventTime" to stopWatch.nanoTime.toString(),
      "responseSize" to responseBody.size.toString(),
      "responseStatus" to responseStatus,
    )
  }

  fun TelemetryClient.dataRequestCompleteNoDate(
    subjectAccessRequest: SubjectAccessRequest?,
    serviceUrl: String,
    stopWatch: StopWatch,
    responseStatus: String,
  ) {
    telemetryClient.trackSarEvent(
      "ServiceDataRequestNoData",
      subjectAccessRequest,
      "serviceURL" to serviceUrl,
      "eventTime" to stopWatch.nanoTime.toString(),
      "responseSize" to "0",
      "responseStatus" to responseStatus,
    )
  }
}
