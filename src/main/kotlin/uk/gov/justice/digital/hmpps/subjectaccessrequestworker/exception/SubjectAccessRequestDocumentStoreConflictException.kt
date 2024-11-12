package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import java.util.UUID

class SubjectAccessRequestDocumentStoreConflictException(
  subjectAccessRequestId: UUID? = null,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(
  "subject access request document store upload unsuccessful: document already exists",
  null,
  ProcessingEvent.STORE_DOCUMENT,
  subjectAccessRequestId,
  params,
)
