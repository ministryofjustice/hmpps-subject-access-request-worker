package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.incremental

import java.io.Closeable
import java.net.URI
import java.util.Base64
import java.util.UUID

interface ImageStore: Closeable {
  fun add(embeddedImage: EmbeddedImage): URI
  fun flush()
}

data class EmbeddedImage(val src: String, val extension: String) {
  fun embeddedImageToByteArray(): ByteArray = Base64.getDecoder().decode(src)
  fun getFilename(id: UUID): String = "$id$extension"
}
