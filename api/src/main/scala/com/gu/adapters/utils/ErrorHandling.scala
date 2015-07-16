package com.gu.adapters.utils

import com.gu.core.Error
import com.gu.core.Errors._
import com.typesafe.scalalogging.LazyLogging

import scala.util.{ Failure, Success, Try }
import scalaz.Scalaz._
import scalaz.{ NonEmptyList, \/ }

object ErrorHandling extends LazyLogging {

  implicit class TryOps[A](val t: Try[A]) extends AnyVal {
    def toDisjunction: \/[Throwable, A] = t match {
      case Success(s) => \/.right(s)
      case Failure(e) => \/.left(e)
    }

    def eventually[Ignore](effect: => Ignore): Try[A] = {
      val ignoring = (_: Any) => { effect; t }
      t transform (ignoring, ignoring)
    }
  }

  def attempt[A](action: => A): Throwable \/ A = {
    val result = Try(action).toDisjunction
    result leftMap { e =>
      logger.error("Attempt failed", e)
    }
    result
  }

  def io[A](action: => A): Error \/ A = {
    attempt(action) leftMap ioError
  }

  def ioError(e: Throwable): Error = ioFailed(NonEmptyList(e.getMessage))

  def logError(msg: String, e: Error): Error = {
    val errors = e.message + " " + e.errors.toList.mkString("(", ", ", ")")
    logger.error(msg + " - cause is: " + errors)
    e
  }

  def logIfError[A](msg: String, result: Error \/ A): Error \/ A = {
    result.bimap(e => logError(msg, e), identity)
  }

}
