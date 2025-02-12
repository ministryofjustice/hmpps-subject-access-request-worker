package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.testutils

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
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
  TemplateTestingUtil().generatePdfFile()
}

class TemplateTestingUtil {

  companion object {
    const val CONFIG_PATH = "/integration-tests/template-testing-util/config.json"
    const val DATA_STUBS_PATH = "/integration-tests/api-response-stubs"
    const val SUBJECT_NAME = "REACHER, Joe"

    val subjectAccessRequest = SubjectAccessRequest(
      id = UUID.fromString("83f1f9af-1036-4273-8252-633f6c7cc1d6"),
      nomisId = "nomis-666",
      ndeliusCaseReferenceId = "ndeliusCaseReferenceId-666",
      sarCaseReferenceNumber = "666",
      dateFrom = LocalDate.of(2024, 1, 1),
      dateTo = LocalDate.of(2025, 1, 1),
    )
  }

  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository, userDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val pdfService: GeneratePdfService = GeneratePdfService(templateRenderService, telemetryClient, DateService())

  init {
    whenever(prisonDetailsRepository.findByPrisonId("MDI")).thenReturn(
      PrisonDetail(
        prisonId = "MDI",
        prisonName = "MOORLAND (HMP & YOI)",
      ),
    )
    whenever(prisonDetailsRepository.findByPrisonId("LEI")).thenReturn(
      PrisonDetail(
        prisonId = "LEI",
        prisonName = "LEEDS (HMP)",
      ),
    )
  }

  fun generatePdfStream(
    config: Config,
    subjectName: String,
    subjectAccessRequest: SubjectAccessRequest,
  ): ByteArrayOutputStream {
    val dpsServices = config.services.map { s ->
      DpsService(
        name = s.name,
        businessName = s.businessName,
        content = s.getContent()?.get("content"),
      )
    }

    return pdfService.execute(
      dpsServices,
      subjectName,
      subjectAccessRequest,
    )
  }

  fun generatePdfFile() {
    val config = loadConfig()
    writePdf(config, generatePdfStream(config, SUBJECT_NAME, subjectAccessRequest))
  }

  private fun loadConfig(): Config {
    val configJson =
      TemplateTestingUtil::class.java.getResource(CONFIG_PATH)?.readText(Charsets.UTF_8)
        ?: throw RuntimeException("$CONFIG_PATH not found")
    return ObjectMapper().readValue(configJson, Config::class.java)
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
    var services: List<Service>,
    var outputDir: String,
  ) {
    constructor() : this(services = mutableListOf(), outputDir = "")
  }

  data class Service(
    val name: String,
    val businessName: String,
  ) {

    constructor() : this("", "")

    fun getContent(): Map<*, *>? {
      val path = Paths.get(DATA_STUBS_PATH).resolve("$name-stub.json")
      val stub = TemplateTestingUtil::class.java.getResource(path.toString())?.readText(Charsets.UTF_8)
        ?: throw RuntimeException("$path not found")

      return ObjectMapper().readValue(stub, Map::class.java)
    }
  }
}
