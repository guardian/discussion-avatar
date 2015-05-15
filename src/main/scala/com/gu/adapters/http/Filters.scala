package com.gu.adapters.http

import java.util.UUID

import com.gu.core.Errors.invalidFilters
import com.gu.core._
import org.scalatra.{ActionResult, Params}

import scalaz._

case class Filters(
  status: Status,
  cursor: Option[UUID],
  reverse: Option[Boolean]
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

    val cursor: Validation[NonEmptyList[String], Option[UUID]] = params.get("cursor") match {
      case Some(UuidRegex(s)) => Success(Some(UUID.fromString(s)))
      case Some(invalid) => Failure(NonEmptyList(s"Cursor '$invalid' is not a valid UUID."))
      case None => Success(None)
    }

    val reverse: Validation[NonEmptyList[String], Option[Boolean]] = params.get("reverse") match {
      case Some(_) => Success(Some(true))
      case None => Success(None)
    }

    val filters = for {
      s <- status
      c <- cursor
      r <- reverse
    } yield Filters(s, c, r)

    filters.leftMap(invalidFilters).disjunction
  }
}