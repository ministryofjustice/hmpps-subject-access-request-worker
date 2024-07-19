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

  fun createOrderedServiceUrlList(configuredOrderedUrlList: List<String>, unorderedSelectedUrlList: MutableList<String>): MutableList<String> {
    val orderedSelectedUrlList = mutableListOf<String>()

    configuredOrderedUrlList.forEach {
      if (unorderedSelectedUrlList.contains(it)) {
        orderedSelectedUrlList.add(it)
        unorderedSelectedUrlList.removeAt(unorderedSelectedUrlList.indexOf(it))
      }
    }

    orderedSelectedUrlList.addAll(unorderedSelectedUrlList)

    return orderedSelectedUrlList
  }

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
}