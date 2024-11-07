package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf

import java.io.ByteArrayOutputStream

/**
 * Wrapper class around ByteArrayOutputStream containing PDF data and the number of pages in the PDF document.
 */
data class CreatePdfResult(val byteArrayOutputStream: ByteArrayOutputStream, val numberOfPages: Int)
