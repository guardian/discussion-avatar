package com.gu.adapters.http

import java.util.UUID

import com.gu.core.Errors.invalidFilters
import com.gu.core._
import org.scalatra.{ActionResult, Params}

import scalaz._

case class Filters(
  status: Status,
  since: Option[UUID],
  until: Option[UUID]
)

object Filters {

  def fromParams[A](params: Params): \/[InvalidFilters, Filters] = {
    val status: Validation[NonEmptyList[String], Status] = params.get("status") match {
      case Some(Inactive.asString) => Success(Inactive)
      case Some(Approved.asString) => Success(Approved)
      case Some(Rejected.asString) => Success(Rejected)
      case Some(Pending.asString) => Success(Pending)
      case Some(invalid) => Failure(NonEmptyList(s"'$invalid' is not a valid status type. Must be '${Inactive.asString}', '${Approved.asString}', '${Rejected.asString}', or '${Pending.asString}'."))
      case None => Success(Approved)
    }

    val UuidRegex = """(^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$)""".r

    val since: Validation[NonEmptyList[String], Option[UUID]] = params.get("since") match {
      case Some(UuidRegex(s)) => Success(Some(UUID.fromString(s)))
      case Some(invalid) => Failure(NonEmptyList(s"'$invalid' is not a valid UUID for since."))
      case None => Success(None)
    }

    val until: Validation[NonEmptyList[String], Option[UUID]] = params.get("until") match {
      case Some(UuidRegex(s)) => Success(Some(UUID.fromString(s)))
      case Some(invalid) => Failure(NonEmptyList(s"'$invalid' is not a valid UUID for until."))
      case None => Success(None)
    }

    // FIXME: can't specify both 'since' and 'until'

    val filters = for {
      s <- status
      f <- since
      b <- until
    } yield Filters(s, f, b)

    filters.leftMap(invalidFilters).disjunction
  }
}