package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import com.openhtmltopdf.extend.FSStreamFactory
import com.openhtmltopdf.swing.NaiveUserAgent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DocumentStoreService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.TempDirectoryService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.ITextServicePdfRenderer
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.OpenHtmlServicePdfRenderer
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.S3fsStreamFactory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.SarResourceRetriever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.ServicePdfRenderer

@Configuration
class RenderConfig(
  val sarResourceRetriever: SarResourceRetriever,
) {

  @Bean
  @ConditionalOnProperty(
    prefix = "application",
    name = ["pdf-uri-retriever"],
    havingValue = "default",
    matchIfMissing = true,
  )
  fun defaultFSStreamFactory(): FSStreamFactory = NaiveUserAgent.DefaultHttpStreamFactory()

  @Bean
  @ConditionalOnProperty(
    prefix = "application",
    name = ["pdf-uri-retriever"],
    havingValue = "s3",
    matchIfMissing = false,
  )
  fun s3FSStreamFactory(
    documentStoreService: DocumentStoreService,
  ): FSStreamFactory = S3fsStreamFactory(
    delegate = NaiveUserAgent.DefaultHttpStreamFactory(),
    documentStoreService = documentStoreService,
  )

  @Bean
  fun servicePdfRenderer(
    applicationProperties: ApplicationProperties,
    tempDirectoryService: TempDirectoryService,
    fsStreamFactory: FSStreamFactory,
  ): ServicePdfRenderer = when (applicationProperties.serviceRenderer) {
    ServiceRenderer.ITEXT -> ITextServicePdfRenderer(sarResourceRetriever)
    ServiceRenderer.OPENHTMLTOPDF -> OpenHtmlServicePdfRenderer(tempDirectoryService, fsStreamFactory)
  }
}

enum class ServiceRenderer {
  ITEXT,
  OPENHTMLTOPDF,
}
