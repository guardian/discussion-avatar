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

  def validate(image: InputStream): Error \/ InputStream = {
    // guessContentTypeFromStream only works with streams than support mark and reset
    val buffered = new BufferedInputStream(image)
    val mimeType = attempt {
      URLConnection.guessContentTypeFromStream(buffered) match {
        case "image/png" | "image/jpeg" => true
        case "image/gif" =>
          val reader = ImageIO.getImageReadersBySuffix("GIF").next
          val iis = ImageIO.createImageInputStream(buffered)
          reader.setInput(iis)
          reader.getNumImages(true) == 1
        case a => false
      }
    }

    mimeType match {
      case \/-(true) => buffered.right
      case _ => invalidMimeType(NonEmptyList("Uploaded images must be of type png, jpeg, or gif (non-animated)")).left
    }
  }
}
