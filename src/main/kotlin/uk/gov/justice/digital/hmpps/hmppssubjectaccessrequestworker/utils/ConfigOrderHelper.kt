package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils

import org.springframework.stereotype.Component
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

  fun extractServicesConfig(configFilename: String): List<String> {
    println("EXTRACTING ${configFilename}")
    val configReader = File(configFilename)
    val configUrlList = configReader.readLines()
    println(configUrlList)
    return configUrlList
  }
}