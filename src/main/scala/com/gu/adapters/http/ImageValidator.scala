package com.gu.adapters.http

import java.io.{BufferedInputStream, InputStream}
import java.net.URLConnection
import javax.imageio.ImageIO

import com.gu.adapters.utils.Attempt.attempt
import com.gu.core.Error
import com.gu.core.Errors.invalidMimeType

import scalaz.Scalaz._
import scalaz.{NonEmptyList, \/, \/-}

object ImageValidator {

  def notAnimated(image: InputStream): Boolean = {
    val buffered = new BufferedInputStream(image)
    val reader = ImageIO.getImageReadersBySuffix("GIF").next
    val iis = ImageIO.createImageInputStream(buffered)
    reader.setInput(iis)
    reader.getNumImages(true) == 1
  }

  def validate(buffered: BufferedInputStream): Error \/ String = {
    // guessContentTypeFromStream only works with streams than support mark and reset
    val mimeType = attempt(URLConnection.guessContentTypeFromStream(buffered))
      .leftMap(_ => invalidMimeType(NonEmptyList("Unable to verify mime type of file")))

    mimeType flatMap {
      case i @ ("image/png" | "image/jpeg") => i.right
      case "image/gif" if notAnimated(buffered) => "image/gif".right
      case _ => invalidMimeType(NonEmptyList("Uploaded images must be of type png, jpeg, or gif (non-animated)")).left
    }
  }
}
