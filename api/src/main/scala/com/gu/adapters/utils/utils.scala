package com.gu.adapters.utils

import java.io.InputStream

import com.gu.adapters.utils.Attempt.attempt
import com.gu.adapters.utils.ToTryOps.toTryOps
import com.gu.core.Error
import com.gu.core.Errors._
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{ DateTime, DateTimeZone }
import org.scalatra.servlet.FileItem

import scala.util.{ Failure, Success, Try }
import scalaz.Scalaz._
import scalaz.{ NonEmptyList, \/ }

object ToTryOps {
  implicit def toTryOps[A](t: Try[A]) = TryOps(t)
}

case class TryOps[A](t: Try[A]) {
  def toDisjunction: \/[Throwable, A] = t match {
    case Success(s) => \/.right(s)
    case Failure(e) => \/.left(e)
  }
}

object ISODateFormatter {
  val dateFormat = ISODateTimeFormat.dateTimeNoMillis.withZone(DateTimeZone.UTC)
  def parse(s: String): DateTime = dateFormat.parseDateTime(s)
  def print(dt: DateTime): String = dateFormat.print(dt)
}

object Attempt extends LazyLogging {
  def attempt[A](action: => A): Throwable \/ A = {
    val result = Try(action).toDisjunction
    result leftMap { e =>
      logger.error("Attempt failed", e)
    }
    result
  }

  def io[A](action: => A): Error \/ A = {
    attempt(action).leftMap(e => ioFailed(NonEmptyList(e.getMessage)))
  }
}

object InputStreamToByteArray {
  def apply(is: InputStream): Array[Byte] = {
    Stream.continually(is.read).takeWhile(-1 != _).map(_.toByte).toArray
  }
}

object StreamFromUrl {
  def apply(url: String): Error \/ InputStream = {
    attempt(new java.net.URL(url).openStream())
      .leftMap(_ => unableToReadAvatarRequest(NonEmptyList("Unable to load image from url: " + url)))
  }
}

object StreamFromBody {
  def apply(fileParams: Map[String, FileItem]): Error \/ (String, InputStream) = {
    for {
      file <- attempt(fileParams("file"))
        .leftMap(_ => unableToReadAvatarRequest(NonEmptyList("Could not parse request body")))
    } yield (file.getName, file.getInputStream)
  }
}

object S3FoldersFromId {
  def apply(id: String): String = {
    id.take(4).toList.mkString("/")
  }
}

object ErrorLogger extends LazyLogging {
  def logError(msg: String, e: Error): Error = {
    val errors = e.message + " " + e.errors.toList.mkString("(", ", ", ")")
    logger.error(msg + " - cause is: " + errors)
    e
  }

  def logIfError[A](msg: String, result: Error \/ A): Error \/ A = {
    result.bimap(e => logError(msg, e), identity)
  }
}
