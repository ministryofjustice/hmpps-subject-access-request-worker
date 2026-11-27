package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["application.service-renderer=openhtmltopdf"])
class SubjectAccessRequestProcessorOpenHtmlIntTest : SubjectAccessRequestProcessorIntTestBase() {
  override val referencePdfBaseDir: String = "$REFERENCE_PDF_BASE_DIR/openhtmltopdf"
}
