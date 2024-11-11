package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.testutils

import com.microsoft.applicationinsights.TelemetryClient
import com.nimbusds.jose.shaded.gson.Gson
import com.nimbusds.jose.shaded.gson.GsonBuilder
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.GeneratePdfService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.TemplateRenderService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

fun main(args: Array<String>) {
  TemplateTestingUtil().generatePdf()
}

class TemplateTestingUtil {

  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val gson = GsonBuilder().setPrettyPrinting().create()

  private val pdfService: GeneratePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  companion object {
    const val CONFIG_PATH = "/pdf/testutil/config/pdf-util-config.json"
    const val DATA_STUBS_PATH = "/pdf/testutil/stubs"
  }

  fun generatePdf() {
    val config = loadConfig()
    val dpsServices = config.services.map { s ->
      DpsService(name = s.name, content = s.getContent()?.get("content"))
    }

    val result = pdfService.execute(
      dpsServices,
      "nomisId",
      "ndeliusCaseReferenceId",
      "sarCaseReferenceNumber",
      "Homer Simpson",
      LocalDate.now().minusYears(1),
      LocalDate.now(),
      subjectAccessRequest = SubjectAccessRequest(id = UUID.randomUUID()),
    )

    writePdf(config, result)
  }

  private fun loadConfig(): Config {
    val configJson =
      TemplateTestingUtil::class.java.getResource(CONFIG_PATH)?.readText(Charsets.UTF_8)
        ?: throw RuntimeException("$CONFIG_PATH not found")
    return gson.fromJson(configJson, Config::class.java)
  }

  private fun writePdf(config: Config, baos: ByteArrayOutputStream) {
    baos.use {
      val outputFile = Paths.get(config.outputDir)
        .resolve("sar-pdf-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddhhmmss"))}.pdf")
      Files.write(outputFile, baos.toByteArray())
      println("Generated pdf: $outputFile")
    }
  }

  data class Config(
    val services: List<Service>,
    val outputDir: String,
  )

  data class Service(
    val name: String,
  ) {

    fun getContent(): Map<*, *>? {
      val path = Paths.get(DATA_STUBS_PATH).resolve("$name-stub.json")
      val stub = TemplateTestingUtil::class.java.getResource(path.toString())?.readText(Charsets.UTF_8)
        ?: throw RuntimeException("$path not found")

      return Gson().fromJson(stub, Map::class.java)
    }
  }
}
