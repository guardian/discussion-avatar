package com.gu.adapters.http

import java.io.InputStream

import com.gu.core.models.Error
import com.gu.core.models.Errors._
import com.gu.core.utils.ErrorHandling._
import org.scalatra.servlet.FileItem

import scala.util.Try
import scalaz.{NonEmptyList, \/}

object IOUtils {
  def readBytesAndCloseInputStream(is: InputStream): Error \/ Array[Byte] = {
    Try { Stream.continually(is.read).takeWhile(-1 != _).map(_.toByte).toArray }
      .eventually { is.close() }
      .toDisjunction leftMap ioError
  }

  def readBytesFromUrl(url: String): Error \/ Array[Byte] = {
    val safeUrl = new java.net.URI(url).toASCIIString
    attempt(new java.net.URL(safeUrl).openStream())
      .flatMap(readBytesAndCloseInputStream)
      .leftMap(_ => unableToReadAvatarRequest(NonEmptyList("Unable to load image from url: " + url)))
  }

  def readBytesFromFile(fileParams: Map[String, FileItem]): Error \/ (String, Array[Byte]) = {
    for {
      file <- attempt(fileParams("file"))
        .leftMap(_ => unableToReadAvatarRequest(NonEmptyList("Could not parse request body")))
    } yield (file.getName, file.get())
  }
}
