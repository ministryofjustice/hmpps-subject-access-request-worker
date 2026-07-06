package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments

import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.Property
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_ATTACHMENTS_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_ATTACHMENTS_EMPTY
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_ATTACHMENTS_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_ATTACHMENT_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_ATTACHMENT_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.Attachment
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.AttachmentInfo
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DocumentStoreService
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AttachmentsPdfServiceTest {

  private val documentStoreService: DocumentStoreService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val defaultPdfRenderer: DefaultPdfRenderer = mock()
  private val textAttachmentPdfRenderer: AttachmentPdfRenderer = mock()
  private val subjectAccessRequest: SubjectAccessRequest = mock()
  private val document: Document = mock()

  private val sarId = UUID.randomUUID()
  private val caseReference = "666"
  private val contextId = UUID.randomUUID()
  private val serviceName = "service-123"

  private lateinit var service: AttachmentsPdfService

  private val attachmentInfo1: AttachmentInfo = AttachmentInfo(
    "attachment1",
    1,
    "attachment_one",
    "text/plain",
    100,
    "attachment_one.txt",
  )

  private val attachment1: Attachment = Attachment(
    data = "attachment one text file".toByteArray().inputStream(),
    info = attachmentInfo1,
  )

  @Captor
  private lateinit var eventCaptor: ArgumentCaptor<String>

  @Captor
  private lateinit var propertiesCaptor: ArgumentCaptor<Map<String, String>>

  @Captor
  private lateinit var paragraphCaptor: ArgumentCaptor<Paragraph>

  @BeforeEach
  fun setup() {
    whenever(subjectAccessRequest.id)
      .thenReturn(sarId)
    whenever(subjectAccessRequest.contextId)
      .thenReturn(contextId)
    whenever(subjectAccessRequest.sarCaseReferenceNumber)
      .thenReturn(caseReference)

    whenever(textAttachmentPdfRenderer.supportedContentTypes())
      .thenReturn(setOf("text/plain"))

    service = AttachmentsPdfService(
      documentStoreService = documentStoreService,
      telemetryClient = telemetryClient,
      defaultPdfRenderer = defaultPdfRenderer,
      renderers = listOf(textAttachmentPdfRenderer),
    )
  }

  @Test
  fun `should do nothing when no attachments exist`() = runTest {
    whenever(documentStoreService.listAttachments(subjectAccessRequest, serviceName))
      .thenReturn(emptyList())

    service.processAttachments(subjectAccessRequest, serviceName, document)

    verify(documentStoreService, times(1)).listAttachments(subjectAccessRequest, serviceName)
    verify(telemetryClient, times(2))
      .trackEvent(
        eventCaptor.capture(),
        propertiesCaptor.capture(),
        isNull(),
      )

    assertTelemetryEventName(
      GENERATE_PDF_ADD_ATTACHMENTS_STARTED.toString(),
      GENERATE_PDF_ADD_ATTACHMENTS_EMPTY.toString(),
    )

    assertTelemetryEventProperties(
      defaultTelemetryProperties(),
      defaultTelemetryProperties(),
    )

    verifyNoMoreInteractions(documentStoreService)
    verifyNoInteractions(document)
  }

  @Test
  fun `should build expected Document when a single attachment exists`() = runTest {
    whenever(documentStoreService.listAttachments(subjectAccessRequest, serviceName))
      .thenReturn(listOf(attachmentInfo1))

    whenever(documentStoreService.getAttachment(subjectAccessRequest, serviceName, attachmentInfo1))
      .thenReturn(attachment1)

    service.processAttachments(subjectAccessRequest, serviceName, document)

    verify(documentStoreService, times(1))
      .listAttachments(subjectAccessRequest, serviceName)

    verify(documentStoreService, times(1))
      .getAttachment(subjectAccessRequest, serviceName, attachmentInfo1)

    verify(document, times(2))
      .add(paragraphCaptor.capture())

    assertThat(paragraphCaptor.allValues).hasSize(2)
    val p1 = paragraphCaptor.allValues[0]
    assertParagraphText(p1, "Attachment: 1")
    assertParagraphTextFontSize(p1, 16f)
    assertParagraphTextAlignment(p1, TextAlignment.CENTER)

    val p2 = paragraphCaptor.allValues[1]
    assertParagraphText(p2, "attachment_one.txt - attachment_one")
    assertParagraphTextAlignment(p2, TextAlignment.LEFT)

    verify(textAttachmentPdfRenderer, times(1))
      .add(document, attachment1)

    verify(telemetryClient, times(4))
      .trackEvent(
        eventCaptor.capture(),
        propertiesCaptor.capture(),
        isNull(),
      )

    assertTelemetryEventName(
      GENERATE_PDF_ADD_ATTACHMENTS_STARTED.toString(),
      GENERATE_PDF_ADD_ATTACHMENT_STARTED.toString(),
      GENERATE_PDF_ADD_ATTACHMENT_COMPLETED.toString(),
      GENERATE_PDF_ADD_ATTACHMENTS_COMPLETED.toString(),
    )

    assertTelemetryEventProperties(
      defaultTelemetryProperties(),
      defaultTelemetryPropertiesWith(Pair("attachmentNumber", "1"), Pair("filename", "attachment_one.txt")),
      defaultTelemetryPropertiesWith(Pair("attachmentNumber", "1"), Pair("filename", "attachment_one.txt")),
      defaultTelemetryProperties(),
    )
  }

  private fun assertParagraphText(actual: Paragraph, expected: String) {
    assertThat(actual.children).hasSizeGreaterThanOrEqualTo(1)
    assertThat(actual.children[0]).isInstanceOf(Text::class.java)
    assertThat((actual.children[0] as Text).text).isEqualTo(expected)
  }

  private fun assertParagraphTextFontSize(actual: Paragraph, expected: Float) {
    assertThat(actual.getProperty<UnitValue>(Property.FONT_SIZE)).isNotNull
    assertThat(actual.getProperty<UnitValue>(Property.FONT_SIZE).value).isEqualTo(expected)
  }

  private fun assertParagraphTextAlignment(actual: Paragraph, expected: TextAlignment) {
    assertThat(actual.getProperty<TextAlignment>(Property.TEXT_ALIGNMENT)).isNotNull
    assertThat(actual.getProperty<TextAlignment>(Property.TEXT_ALIGNMENT)).isEqualTo(expected)
  }

  private fun assertTelemetryEventName(vararg events: String) {
    assertThat(eventCaptor.allValues).hasSize(events.size)
    events.forEachIndexed { index, eventName ->
      assertThat(eventCaptor.allValues[index]).isEqualTo(eventName)
    }
  }

  private fun assertTelemetryEventProperties(vararg expected: Map<String, String>) {
    assertThat(eventCaptor.allValues).hasSize(expected.size)
    expected.forEachIndexed { index, expectedValue ->
      assertThat(propertiesCaptor.allValues[index]).containsExactlyInAnyOrderEntriesOf(expectedValue)
    }
  }

  private fun defaultTelemetryProperties(): MutableMap<String, String> = mutableMapOf(
    "service" to serviceName,
    "sarId" to caseReference,
    "UUID" to sarId.toString(),
    "contextId" to contextId.toString(),
  )

  private fun defaultTelemetryPropertiesWith(vararg pairs: Pair<String, String>): MutableMap<String, String> {
    val props = mutableMapOf(
      "service" to serviceName,
      "sarId" to caseReference,
      "UUID" to sarId.toString(),
      "contextId" to contextId.toString(),
    )

    props.putAll(pairs.toMap())
    return props
  }
}
