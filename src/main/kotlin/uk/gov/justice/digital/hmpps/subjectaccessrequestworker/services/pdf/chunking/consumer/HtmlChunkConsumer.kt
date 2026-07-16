package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking.consumer

import aws.smithy.kotlin.runtime.io.Closeable

interface HtmlChunkConsumer : Closeable {

  fun consume(chunk: String)
}