package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.testutils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.microsoft.applicationinsights.TelemetryClient
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.LocationsApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.NomisMappingApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.LocationDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.LocationDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.GeneratePdfService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.TemplateRenderService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateResources
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

const val REPORT_GENERATION_DATE = "1 January 2025"
const val REPORT_GENERATION_DATE_FORMAT = "d MMMM yyyy"

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
    fun getReportGenerationDate(): LocalDate = LocalDate.parse(
      REPORT_GENERATION_DATE,
      DateTimeFormatter.ofPattern(REPORT_GENERATION_DATE_FORMAT),
    )

    /**
     * Return the fixed date used in all generated reports in the format expected in the report
     */
    @JvmStatic
    fun getFormattedReportGenerationDate(): String = REPORT_GENERATION_DATE

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
  private val locationDetailsRepository: LocationDetailsRepository = mock()
  private val locationsApiClient: LocationsApiClient = mock()
  private val nomisMappingApiClient: NomisMappingApiClient = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository, userDetailsRepository, locationDetailsRepository, locationsApiClient, nomisMappingApiClient)
  private val templateResources = TemplateResources(
    templatesDirectory = "/templates",
    mandatoryServiceTemplates = listOf("G1", "G2", "G3"),
  )
  private val templateRenderService = TemplateRenderService(templateHelpers, templateResources)
  private val telemetryClient: TelemetryClient = mock()
  private val dateService: DateService = mock()
  private val pdfService: GeneratePdfService = GeneratePdfService(templateRenderService, telemetryClient, dateService)

  init {
    addPrisonMapping("MDI", "MOORLAND (HMP & YOI)")
    addPrisonMapping("LEI", "LEEDS (HMP)")

    addLocationMapping("cac85758-380b-49fc-997f-94147e2553ac", 357591, "ASSO A WING")
    addLocationMapping("d0763236-c073-4ef4-9592-419bf0cd72cb", 357592, "ASSO B WING")
    addLocationMapping("8ac39ebb-499d-4862-ae45-0b091253e89d", 27187, "ADJ")

    whenever(dateService.now()).thenReturn(getReportGenerationDate())
  }

  private fun addPrisonMapping(prisonId: String, prisonName: String) {
    whenever(prisonDetailsRepository.findByPrisonId(prisonId)).thenReturn(
      PrisonDetail(
        prisonId = prisonId,
        prisonName = prisonName,
      ),
    )
  }

  private fun addLocationMapping(locationId: String, nomisId: Int, locationName: String) {
    val locationDetail = LocationDetail(dpsId = locationId, nomisId = nomisId, name = locationName)
    whenever(locationDetailsRepository.findByDpsId(locationId)).thenReturn(locationDetail)
    whenever(locationDetailsRepository.findByNomisId(nomisId)).thenReturn(locationDetail)
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
        .resolve("sar-${getFilename()}.pdf")
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
