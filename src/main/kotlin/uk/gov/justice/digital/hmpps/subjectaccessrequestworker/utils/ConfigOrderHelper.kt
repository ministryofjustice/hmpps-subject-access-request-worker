package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.yaml.snakeyaml.LoaderOptions
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfig

@Component
class ConfigOrderHelper(
  @Value("\${G1-api.url}") val G1: String,
  @Value("\${G2-api.url}") val G2: String,
  @Value("\${G3-api.url}") val G3: String,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun extractServicesConfig(configFilename: String): ServiceConfig? {
    val loaderOptions = LoaderOptions()
    loaderOptions.codePointLimit = 1024 * 1024 * 1024
    val yamlFactory = YAMLFactory.builder()
      .loaderOptions(loaderOptions)
      .build()
    val mapper = ObjectMapper(yamlFactory)

    val resource = ClassPathResource(configFilename)
    val serviceConfigObject = mapper.readValue(resource.inputStream, ServiceConfig::class.java)

    return serviceConfigObject
  }

  fun getDpsServices(servicesMap: Map<String, String>): List<DpsService> {
    val dpsServices = mutableListOf<DpsService>()
    servicesMap.forEach { (key, value) ->
      val url = when (key) {
        "G1" -> {
          // temp logging to ensure the correct URL is being used
          log.info("Replacing G1 with URL: $G1")
          G1
        }
        "G2" -> {
          // temp logging to ensure the correct URL is being used
          log.info("Replacing G2 with URL: $G2")
          G2
        }
        "G3" -> {
          // temp logging to ensure the correct URL is being used
          log.info("Replacing G3 with URL: $G3")
          G3
        }
        else -> value
      }
      dpsServices.add(DpsService(url = url, name = key))
    }
    return dpsServices
  }
}
