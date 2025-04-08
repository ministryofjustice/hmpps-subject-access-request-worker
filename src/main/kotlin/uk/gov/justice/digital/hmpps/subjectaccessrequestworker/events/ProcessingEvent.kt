package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events

/**
 * ProcessingEvent represent the stages of processing a subject access request.
 */
enum class ProcessingEvent {
  GET_UNCLAIMED_REQUESTS,
  CLAIM_REQUEST,
  COMPLETE_REQUEST,
  GET_SAR_DATA,
  STORE_DOCUMENT,
  ACQUIRE_AUTH_TOKEN,
  GET_OFFENDER_NAME,
  GET_SERVICE_CONFIGURATION,
  RENDER_TEMPLATE,
  GET_LOCATION,
  GET_LOCATION_MAPPING,
  HTML_RENDERER_REQUEST,
  GET_RENDERED_HTML_DOCUMENT,
}
