package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.springframework.stereotype.Component
import org.yaml.snakeyaml.LoaderOptions
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.ServiceDetails
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

  fun extractServicesConfig(configFilename: String): ServiceDetails {
    println("EXTRACTING ${configFilename}")
    val configReader = File(configFilename)
    val configString = configReader.readText()
    //val configUrlList = configReader.readLines()
    //println(configUrlList)
    val loaderOptions = LoaderOptions()
    loaderOptions.codePointLimit = 1024 * 1024 * 1024
    val yamlFactory = YAMLFactory.builder()
      .loaderOptions(loaderOptions)
      .build()
    val mapper = ObjectMapper(yamlFactory)
    val testService = mapper.readValue(File(configFilename), ServiceDetails::class.java)
//    val contentText =
//      YAMLMapper(yamlFactory.enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)).reader(configString)
    return testService
  }
}