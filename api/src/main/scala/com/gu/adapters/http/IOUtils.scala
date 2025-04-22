package com.gu.adapters.http

import java.io.InputStream

import com.gu.core.models.Error
import com.gu.core.models.Errors._
import com.gu.core.utils.ErrorHandling._
import org.scalatra.servlet.FileItem

import scala.util.Try

object IOUtils {
  def readBytesAndCloseInputStream(is: InputStream): Either[Error, Array[Byte]] = {
    Try { Stream.continually(is.read).takeWhile(-1 != _).map(_.toByte).toArray }
      .eventually { is.close() }
      .toEither
      .left.map(ioError)
  }

  def readBytesFromUrl(url: String): Either[Error, Array[Byte]] = {
    val safeUrl = new java.net.URI(url).toASCIIString
    attempt(new java.net.URL(safeUrl).openStream())
      .toEither
      .flatMap(readBytesAndCloseInputStream)
      .left.map(_ => unableToReadAvatarRequest(List("Unable to load image from url: " + url)))
  }

  def readBytesFromFile(fileParams: Map[String, FileItem]): Either[Error, (String, Array[Byte])] = {
    for {
      file <- attempt(fileParams("file")).toEither
        .left.map(_ => unableToReadAvatarRequest(List("Could not parse request body")))
    } yield (file.getName, file.get())
  }
}
