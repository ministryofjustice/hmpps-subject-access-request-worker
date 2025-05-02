package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import com.fasterxml.jackson.databind.ObjectMapper
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.LocationsApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.NomisMappingApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.LocationDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.UserDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.LocationDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.TemplateRenderService
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.notExists

const val SERVICE_RESPONSE_STUBS_DIR = "integration-tests/api-response-stubs"
const val REFERENCE_HTML_STUBS_DIR = "integration-tests/html-stubs"

/**
 * Developer util class to aid template development/testing.
 */
fun main(args: Array<String>?) {
  if (args.isNullOrEmpty()) {
    throw IllegalArgumentException("'--service' argument required")
  }

  TemplateGeneratorUtil().generateServiceHtml(args[0])
}

class TemplateGeneratorUtil {
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val locationDetailsRepository: LocationDetailsRepository = mock()
  private val locationsApiClient: LocationsApiClient = mock()
  private val nomisMappingApiClient: NomisMappingApiClient = mock()

  private val templateResources: TemplateResources = TemplateResources()

  private val templateHelpers = TemplateHelpers(
    prisonDetailsRepository,
    userDetailsRepository,
    locationDetailsRepository,
    locationsApiClient,
    nomisMappingApiClient,
  )

  private val templateService: TemplateRenderService = TemplateRenderService(templateHelpers, templateResources)

  init {
    whenever(userDetailsRepository.findByUsername(any())).doAnswer {
      val input = it.arguments[0] as String
      UserDetail(input, "Homer Simpson")
    }

    whenever(prisonDetailsRepository.findByPrisonId(any())).doAnswer {
      val id = it.arguments[0] as String
      PrisonDetail(id, "HMPPS Mordor").also {
        println("subbed $id to Mordor")
      }
    }

    whenever(locationDetailsRepository.findByDpsId(any())).doAnswer {
      val dpsId = it.arguments[0] as String
      LocationDetail(dpsId, 666, "Hogwarts")
    }
  }

  fun generateServiceHtml(serviceName: String) {
    println()
    log("Rendering templates for $serviceName")

    try {
      val output = renderServiceHtml(
        sar = SubjectAccessRequest(id = UUID.randomUUID()),
        service = getService(serviceName),
      ).use { os -> writeToFile(serviceName, os) }

      println()
      log("Successfully generated HTML for service $serviceName:")
      log("file:///$output\n")
    } catch (e: Exception) {
      log("failed to render template for service $serviceName")
      e.message?.let { log(it) }
      throw e
    }
  }

  private fun getService(
    serviceName: String,
  ): DpsService = DpsService(
    name = serviceName,
    businessName = serviceName,
    content = getServiceResponseStubData(serviceName),
  )

  private fun writeToFile(serviceName: String, os: ByteArrayOutputStream): Path {
    val output = getOutputFile(serviceName)
    Files.write(output, os.toByteArray())
    return output
  }

  private fun renderServiceHtml(sar: SubjectAccessRequest, service: DpsService): ByteArrayOutputStream {
    val renderedTemplate = templateService.renderTemplate(
      subjectAccessRequest = sar,
      serviceName = service.name!!,
      serviceData = service.content,
    )

    val bytes: ByteArray = renderedTemplate?.toByteArray() ?: ByteArray(0)
    return ByteArrayOutputStream(renderedTemplate?.toByteArray()?.size ?: 0).use { os ->
      os.write(bytes, 0, bytes.size)
      os
    }
  }

  private fun getServiceResponseStubData(serviceName: String): Any? {
    val path = assertResponseStubJsonExists(serviceName)
    log("Using response stub data: file:///$path ")

    return FileInputStream(path.toFile()).use { inputStream ->
      inputStream.use {
        ObjectMapper().readValue(inputStream, Map::class.java)
      }
    }.let { it["content"] }
  }

  private fun assertResponseStubJsonExists(serviceName: String): Path {
    val target = getResponseJsonStub(serviceName)

    if (target.notExists()) {
      throw responseStubJsonNotFoundException(target)
    }

    return target
  }

  private fun getOutputFile(serviceName: String) = Paths.get(getTestResourcesDir())
    .resolve("$REFERENCE_HTML_STUBS_DIR/$serviceName-expected.html")

  private fun getResponseJsonStub(serviceName: String) = Paths.get(getTestResourcesDir())
    .resolve("$SERVICE_RESPONSE_STUBS_DIR/$serviceName-stub.json")

  private fun getTestResourcesDir() = System.getenv("TEST_RESOURCES_DIR")

  private fun responseStubJsonNotFoundException(target: Path) =
    RuntimeException("expected service response stub json [$target] but not found")

  private fun log(message: String) {
    println("[generateHtml] $message")
  }
}
