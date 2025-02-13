package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.testutils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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
import java.nio.file.Path
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
    const val CONFIG_PATH = "/integration-tests/template-testing-util/template-testing-config.yml"
    const val DATA_STUBS_PATH = "/integration-tests/api-response-stubs"
    const val SUBJECT_NAME = "REACHER, Joe"

    /**
     * Use a fixed date in all generated reports.
     */
    @JvmStatic
    val reportGenerationDate: LocalDate = LocalDate.of(2025, 1, 1)

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
  private val dateService: DateService = mock()
  private val pdfService: GeneratePdfService = GeneratePdfService(templateRenderService, telemetryClient, dateService)

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

    whenever(dateService.now()).thenReturn(reportGenerationDate)
  }

  fun generatePdfStream(
    config: Config,
    subjectName: String,
    subjectAccessRequest: SubjectAccessRequest,
  ): ByteArrayOutputStream {
    val targetServices = config.targetServices

    val dpsServices = config.services
      .filter { s -> targetServices.contains(s.name) }
      .map { s ->
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
    val config = readConfig()
    writePdf(config, generatePdfStream(config, SUBJECT_NAME, subjectAccessRequest))
  }

  private fun writePdf(config: Config, baos: ByteArrayOutputStream) {
    baos.use {
      val outputFile = Paths
        .get(config.outputDir)
        .resolve("${getFilename()}.pdf")
      Files.write(outputFile, baos.toByteArray())
      println("Generated pdf: $outputFile")
    }
  }

  private fun getFilename(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddhhmmss"))

  private fun readConfig(): Config {
    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    return TemplateTestingUtil::class.java.getResourceAsStream(CONFIG_PATH).use { inputStream ->
      mapper.readValue(inputStream, Config::class.java)
    }
  }

  data class Config(
    var services: List<Service>,
    var outputDir: String,
    var targetServices: List<String>,
  ) {
    constructor() : this(services = mutableListOf(), outputDir = "", targetServices = mutableListOf())
  }

  data class Service(
    val name: String,
    val businessName: String,
    var order: Int,
  ) {

    constructor() : this("", "", -1)

    fun getContent(): Map<*, *>? {
      val path = getDataJsonStubPath()
      val stub = TemplateTestingUtil::class.java.getResource(path.toString())
        ?.readText(Charsets.UTF_8)
        ?: throw RuntimeException("$path not found")

      return ObjectMapper().readValue(stub, Map::class.java)
    }

    private fun getDataJsonStubPath(): Path = Paths.get(DATA_STUBS_PATH).resolve("$name-stub.json")
  }
}
