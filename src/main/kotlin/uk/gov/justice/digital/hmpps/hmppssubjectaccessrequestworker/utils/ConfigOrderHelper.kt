package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.springframework.stereotype.Component
import org.yaml.snakeyaml.LoaderOptions
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.ServiceConfig
import java.io.File

@Component
class ConfigOrderHelper {

  fun extractServicesConfig(configFilename: String): ServiceConfig? {
    val loaderOptions = LoaderOptions()
    loaderOptions.codePointLimit = 1024 * 1024 * 1024
    val yamlFactory = YAMLFactory.builder()
      .loaderOptions(loaderOptions)
      .build()
    val mapper = ObjectMapper(yamlFactory)

    val serviceConfigObject = mapper.readValue(File(configFilename), ServiceConfig::class.java)

    return serviceConfigObject
  }

  fun getDpsServices(servicesMap: Map<String, String>): List<DpsService> {
    val dpsServices = mutableListOf<DpsService>()
    servicesMap.forEach { (key, value) ->
      dpsServices.add(DpsService(url = value, name = key))
    }
    return dpsServices
  }
}
