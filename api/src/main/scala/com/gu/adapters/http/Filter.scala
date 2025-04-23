package com.gu.adapters.http

import com.gu.core.models.Errors.invalidFilters
import com.gu.core.models._
import com.gu.core.utils.ErrorHandling.attempt
import com.gu.core.utils.ISODateFormatter
import org.joda.time.DateTime
import org.scalatra.Params

object Filter {

  def fromParams[A](params: Params): Either[InvalidFilters, Filters] = {
    val status: Either[List[String], Status] = params.get("status") match {
      case Some(Inactive.asString) => Right(Inactive)
      case Some(Approved.asString) => Right(Approved)
      case Some(Rejected.asString) => Right(Rejected)
      case Some(Pending.asString) => Right(Pending)
      case Some(invalid) => Left(List(s"'$invalid' is not a valid status type. Must be '${Inactive.asString}', '${Approved.asString}', '${Rejected.asString}', or '${Pending.asString}'."))
      case None => Right(Approved)
    }

    val since: Either[List[String], Option[DateTime]] = params.get("since") match {
      case Some(s) => attempt(Some(ISODateFormatter.parse(s))).toEither
        .left.map(_ => List(s"'$s' is not a valid datetime format for 'since'. Must be 'YYYY-MM-DDThh:mm:ssZ'"))
      case None => Right(None)
    }

    val until: Either[List[String], Option[DateTime]] = params.get("until") match {
      case Some(s) => attempt(Some(ISODateFormatter.parse(s))).toEither
        .left.map(_ => List(s"'$s' is not a valid datetime format for 'until'. Must be 'YYYY-MM-DDThh:mm:ssZ'"))
      case None => Right(None)
    }

    val order: Either[List[String], Option[OrderBy]] = params.get("order") match {
      case Some(Descending.asString) => Right(Some(Descending))
      case Some(Ascending.asString) => Right(Some(Ascending))
      case Some(invalid) => Left(List(s"'$invalid' is not a valid order type. Must be '${Ascending.asString}' or '${Descending.asString}' (default)."))
      case None => Right(None)
    }

    val errors = List(status, since, until, order).collect {
      case Left(err) => err
    }.flatten

    val filters = for {
      s <- status
      f <- since
      b <- until
      o <- order
    } yield Filters(s, f, b, o)

    filters
      .filterOrElse(f => f.since.isEmpty || f.until.isEmpty, List("Cannot specify both 'since' and 'until' parameters"))
      .left.map(_ => invalidFilters(errors))
  }

  def queryString(f: Filters): String = {
    val params = List(
      "status" -> Some(f.status.asString),
      "since" -> f.since.map(t => ISODateFormatter.print(t)),
      "until" -> f.until.map(t => ISODateFormatter.print(t)),
      "order" -> f.order.map(_.asString)
    ) collect {
        case (key, Some(value)) => s"$key=$value"
      }

    params.mkString("?", "&", "")
  }
}
