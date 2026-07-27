package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["application.service-renderer=itext"])
class SubjectAccessRequestProcessorITextIntTest : SubjectAccessRequestProcessorIntTestBase() {
  override val referencePdfBaseDir: String = "$REFERENCE_PDF_BASE_DIR/itext"
}
