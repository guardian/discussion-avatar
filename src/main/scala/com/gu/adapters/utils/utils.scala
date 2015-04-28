package com.gu.adapters.utils

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import com.gu.adapters.utils.ToTryOps.toTryOps
import com.gu.core.Errors.ioFailed
import com.gu.core.Error

import scala.util.{Success, Failure, Try}
import scalaz.{\/, NonEmptyList}

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
  val dateFormat = ISODateTimeFormat.dateTimeNoMillis
  def parse(s: String): DateTime = dateFormat.parseDateTime(s)
  def print(dt: DateTime): String = dateFormat.print(dt)
}

object IO {
  def io[A](action: => A): Error \/ A = {
    Try(action).toDisjunction.leftMap { error =>
      ioFailed(NonEmptyList(error.getMessage))
    }
  }
}
