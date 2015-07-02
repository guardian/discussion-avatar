package com.gu.adapters.http

import java.io.{ ByteArrayInputStream, BufferedInputStream, InputStream }
import java.net.URLConnection
import javax.imageio.ImageIO

import com.gu.adapters.utils.Attempt.attempt
import com.gu.core.Error
import com.gu.core.Errors.invalidMimeType

import scalaz.Scalaz._
import scalaz.{ NonEmptyList, \/, \/- }

object ImageValidator {

  def notAnimated(image: InputStream): Boolean = {
    val reader = ImageIO.getImageReadersBySuffix("GIF").next
    val iis = ImageIO.createImageInputStream(image)
    reader.setInput(iis)
    reader.getNumImages(true) == 1
  }

  def validate(image: Array[Byte]): Error \/ String = {
    // guessContentTypeFromStream only works with streams than support mark and reset
    val is = new ByteArrayInputStream(image)
    val mimeType = attempt(URLConnection.guessContentTypeFromStream(is))
      .leftMap(_ => invalidMimeType(NonEmptyList("Unable to verify mime type of file")))

    mimeType flatMap {
      case i @ ("image/png" | "image/jpeg") => i.right
      case "image/gif" if notAnimated(is) => "image/gif".right
      case _ => invalidMimeType(NonEmptyList("Uploaded images must be of type png, jpeg, or gif (non-animated)")).left
    }
  }
}
