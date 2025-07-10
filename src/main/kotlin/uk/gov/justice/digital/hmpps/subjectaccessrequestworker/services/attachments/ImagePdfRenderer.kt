package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments

import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.Attachment

private const val MAX_PAGE_PROPORTION = 0.75f

@Component
class ImagePdfRenderer : AttachmentPdfRenderer {

  override fun add(document: Document, attachment: Attachment) {
    val image = Image(ImageDataFactory.create(attachment.data.readBytes()))

    val pageSize = document.pdfDocument.defaultPageSize
    val pageDimensions = Dimensions(pageSize.width, pageSize.height)

    val scaledImageDimensions = scaleImage(image, pageDimensions)

    centreImage(image, pageDimensions, scaledImageDimensions)

    document.add(image)
  }

  override fun supportedContentTypes(): Set<String> = setOf("image/jpeg", "image/png", "image/gif", "image/tiff")

  private fun scaleImage(image: Image, pageDimensions: Dimensions): Dimensions {
    val maxDimensions = pageDimensions.applyScale(MAX_PAGE_PROPORTION)
    val imageDimensions = Dimensions(image.imageWidth, image.imageHeight)
    val scaledDimensions = imageDimensions.applyScaleToFit(maxDimensions)
    image.scaleAbsolute(scaledDimensions.width, scaledDimensions.height)
    return scaledDimensions
  }

  private fun centreImage(image: Image, pageDimensions: Dimensions, imageDimensions: Dimensions) {
    val xPosition = (pageDimensions.width - imageDimensions.width) / 2
    val yPosition = (pageDimensions.height - imageDimensions.height) / 2
    image.setFixedPosition(xPosition, yPosition)
  }

  data class Dimensions(val width: Float, val height: Float) {
    fun applyScale(scale: Float): Dimensions = Dimensions(width * scale, height * scale)
    fun getScaleToFit(dimensions: Dimensions): Float = minOf(dimensions.width / this.width, dimensions.height / this.height)
    fun applyScaleToFit(dimensions: Dimensions): Dimensions = this.applyScale(getScaleToFit(dimensions))
  }
}
