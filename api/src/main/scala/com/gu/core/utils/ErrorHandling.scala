package com.gu.core.utils

import com.gu.core.models.Error
import com.gu.core.models.Errors._
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Failure, Success, Try}

object ErrorHandling extends LazyLogging {

  implicit class TryOps[A](val t: Try[A]) extends AnyVal {
    def eventually[Ignore](effect: => Ignore): Try[A] = {
      val ignoring = (_: Any) => { effect; t }
      t transform (ignoring, ignoring)
    }
  }

  def attempt[A](action: => A): Try[A] = {
    Try(action) match {
      case Success(v) => Success(v)
      case Failure(e) =>
        logger.error("Attempt failed", e)
        Failure(e)
    }
  }

  def handleIoErrors[A](action: => A): Either[Error, A] = {
    attempt(action) match {
      case Success(v) => Right(v)
      case Failure(e) => Left(ioError(e))
    }
  }

  def ioError(e: Throwable): Error = ioFailed(List(e.getMessage))

  def logError(msg: String, e: Error, statusCode: Option[Int] = None): Error = {
    val errors = e.message + " " + e.errors.toList.mkString("(", ", ", ")")
    statusCode match {
      case Some(statusCode) => logger.error(s"$msg - status code=${statusCode} - cause is: $errors")
      case _ => logger.error(s"$msg - cause is: $errors")
    }
    e
  }

  def logIfError[A](msg: String, result: Either[Error, A]): Either[Error, A] = {
    result.left.foreach(e => logError(msg = msg, e = e))
    result
  }
}
