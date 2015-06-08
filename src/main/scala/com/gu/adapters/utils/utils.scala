package com.gu.adapters.utils

import java.io.InputStream

import com.gu.adapters.utils.Attempt._
import org.joda.time.{ DateTimeZone, DateTime }
import org.joda.time.format.ISODateTimeFormat
import com.gu.adapters.utils.ToTryOps.toTryOps
import com.gu.core.Errors._
import com.gu.core.Error
import org.scalatra.servlet.FileItem

import scala.util.{ Success, Failure, Try }
import scalaz.{ \/, NonEmptyList }

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

object Attempt {
  def attempt[A](action: => A): Throwable \/ A = {
    Try(action).toDisjunction
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
      .leftMap(_ => ioFailed(NonEmptyList("Unable to load image from url: " + url)))
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

object MakeS3Folder {
  def apply(id: String): String = {
    id.substring(0, 6).toList.mkString("/")
  }
}
