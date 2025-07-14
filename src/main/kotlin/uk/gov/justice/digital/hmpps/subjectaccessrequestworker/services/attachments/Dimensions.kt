package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments

data class Dimensions(val width: Float, val height: Float) {
  fun applyScale(scale: Float): Dimensions = Dimensions(width * scale, height * scale)
  fun getScaleToFit(dimensions: Dimensions): Float = minOf(dimensions.width / this.width, dimensions.height / this.height)
  fun applyScaleToFit(dimensions: Dimensions): Dimensions = this.applyScale(getScaleToFit(dimensions))
  fun getPositionToCentreIn(dimensions: Dimensions): Coordinate = Coordinate((dimensions.width - this.width) / 2, (dimensions.height - this.height) / 2)
}

data class Coordinate(val x: Float, val y: Float)
