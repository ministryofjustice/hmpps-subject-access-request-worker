package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.springframework.test.context.TestPropertySource

/**
 * Test verifies the ITextIncremental PDF Implementation produces PDFS identical to the original iText generated PDFs.
 */
@TestPropertySource(properties = ["application.service-renderer=itext_incremental"])
class SubjectAccessRequestProcessorITextIncrementalIntTest : SubjectAccessRequestProcessorIntTestBase() {
  override val referencePdfBaseDir: String = "$REFERENCE_PDF_BASE_DIR/itext"
}
