package com.gu.adapters.http

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URLConnection
import javax.imageio.ImageIO

import com.gu.adapters.http.IOUtils._
import com.gu.core.models.Error
import com.gu.core.models.Errors.invalidMimeType
import com.gu.core.utils.ErrorHandling.attempt
import org.scalatra.servlet.FileItem

import scalaz.Scalaz._
import scalaz.{NonEmptyList, \/}

object Image {

  def getImageFromUrl(url: String): Error \/ (Array[Byte], String) = {
    for {
      bytes <- readBytesFromUrl(url)
      mimeType <- validate(bytes)
    } yield (bytes, mimeType)
  }

  def getImageFromFile(fileParams: Map[String, FileItem]): Error \/ (Array[Byte], String, String) = {
    for {
      nameAndBytes <- readBytesFromFile(fileParams)
      (fname, bytes) = nameAndBytes
      mimeType <- validate(bytes)
    } yield (bytes, mimeType, fname)
  }

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
