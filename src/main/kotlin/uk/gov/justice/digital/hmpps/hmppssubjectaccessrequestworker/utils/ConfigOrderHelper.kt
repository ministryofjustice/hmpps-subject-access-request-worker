package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.springframework.stereotype.Component
import org.yaml.snakeyaml.LoaderOptions
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsServices
import java.io.File

@Component
class ConfigOrderHelper {

  fun extractServicesConfig(configFilename: String): DpsServices? {
    val loaderOptions = LoaderOptions()
    loaderOptions.codePointLimit = 1024 * 1024 * 1024
    val yamlFactory = YAMLFactory.builder()
      .loaderOptions(loaderOptions)
      .build()
    val mapper = ObjectMapper(yamlFactory)

    val dpsServicesObject = mapper.readValue(File(configFilename), DpsServices::class.java)

    return dpsServicesObject
  }

  fun getDpsServices(servicesMap: Map<String, String>): DpsServices {
    val dpsServicesObject = DpsServices()
    servicesMap.forEach { (key, value) ->
      dpsServicesObject.dpsServices.add(DpsService(url = value, name = key))
    }
    return dpsServicesObject
  }
}