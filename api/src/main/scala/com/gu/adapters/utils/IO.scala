package com.gu.adapters.utils

import java.io.InputStream

import com.gu.adapters.utils.ErrorHandling._
import com.gu.core.Error
import com.gu.core.Errors._
import org.scalatra.servlet.FileItem

import scala.util.Try
import scalaz.{ NonEmptyList, \/ }

object IO {
  def readBytesAndCloseInputStream(is: InputStream): Error \/ Array[Byte] = {
    Try { Stream.continually(is.read).takeWhile(-1 != _).map(_.toByte).toArray }
      .eventually { is.close() }
      .toDisjunction leftMap ioError
  }

  def readBytesFromUrl(url: String): Error \/ Array[Byte] = {
    attempt(new java.net.URL(url).openStream()) flatMap readBytesAndCloseInputStream leftMap { _ =>
      unableToReadAvatarRequest(NonEmptyList("Unable to load image from url: " + url))
    }
  }

  def readBytesFromFile(fileParams: Map[String, FileItem]): Error \/ (String, Array[Byte]) = {
    for {
      file <- attempt(fileParams("file"))
        .leftMap(_ => unableToReadAvatarRequest(NonEmptyList("Could not parse request body")))
    } yield (file.getName, file.get())
  }
}
