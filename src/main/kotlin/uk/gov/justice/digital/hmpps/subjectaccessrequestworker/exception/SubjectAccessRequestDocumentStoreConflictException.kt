package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.ErrorCode.Companion.DOCUMENT_STORE_CONFLICT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

class SubjectAccessRequestDocumentStoreConflictException(
  subjectAccessRequest: SubjectAccessRequest? = null,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(
  "subject access request document store upload unsuccessful: document already exists",
  null,
  ProcessingEvent.STORE_DOCUMENT,
  DOCUMENT_STORE_CONFLICT,
  subjectAccessRequest,
  params,
)
