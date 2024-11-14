package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import org.apache.tomcat.util.json.JSONParser
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.ACQUIRE_AUTH_TOKEN
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import java.util.Base64

@Component
class HmppsAuthGateway(
  @Value("\${hmpps-auth.url}") val hmppsAuthUrl: String,
  @Value("\${hmpps-auth.client-id}") var clientId: String,
  @Value("\${hmpps-auth.client-secret}") var clientSecret: String,
) {
  private val webClient: WebClient = WebClient.builder().baseUrl(hmppsAuthUrl).build()

  fun getClientToken(): String {
    val encodedCredentials = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
    val basicAuthCredentials = "Basic $encodedCredentials"

    return try {
      val response = webClient
        .post()
        .uri("/oauth/token?grant_type=client_credentials")
        .header("Authorization", basicAuthCredentials)
        .retrieve()
        .bodyToMono(String::class.java)
        .block()

      JSONParser(response).parseObject()["access_token"].toString()
    } catch (ex: WebClientResponseException) {
      throw subjectAccessRequestWebClientResponseEx(ex)
    } catch (ex: Exception) {
      throw subjectAccessRequestGeneralException(ex)
    }
  }

  private fun subjectAccessRequestWebClientResponseEx(cause: WebClientResponseException): SubjectAccessRequestException =
    SubjectAccessRequestException(
      message = "authGateway get auth token WebclientResponseException",
      cause = cause,
      event = ACQUIRE_AUTH_TOKEN,
      subjectAccessRequestId = null,
      params = mapOf(
        "authority" to cause.request?.uri?.authority,
        "httpStatus" to cause.statusCode.value(),
        "body" to cause.responseBodyAsString,
      ),
    )

  private fun subjectAccessRequestGeneralException(cause: Exception) = SubjectAccessRequestException(
    message = "authGateway get auth token unexpected error",
    cause = cause,
    event = ACQUIRE_AUTH_TOKEN,
    subjectAccessRequestId = null,
    params = mapOf(
      "host" to hmppsAuthUrl,
    ),
  )
}
