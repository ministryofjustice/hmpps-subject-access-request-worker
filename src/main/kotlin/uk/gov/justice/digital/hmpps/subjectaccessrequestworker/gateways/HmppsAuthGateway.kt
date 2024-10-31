package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import org.apache.tomcat.util.json.JSONParser
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.Base64

@Component
class HmppsAuthGateway(
  @Value("\${hmpps-auth.url}") hmppsAuthUrl: String,
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
    } catch (exception: WebClientRequestException) {
      throw RuntimeException("Connection to ${exception.uri.authority} failed.")
    } catch (exception: WebClientResponseException.ServiceUnavailable) {
      throw RuntimeException("${exception.request?.uri?.authority} is unavailable.")
    } catch (exception: WebClientResponseException.Unauthorized) {
      throw RuntimeException("Invalid credentials used.")
    }
  }
}
