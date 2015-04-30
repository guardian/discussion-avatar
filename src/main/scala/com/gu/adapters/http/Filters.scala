package com.gu.adapters.http

import com.gu.core.Errors.invalidFilters
import com.gu.core._
import org.scalatra.{ActionResult, Params}

import scalaz._

case class Filters(status: Status)

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

    status.bimap(invalidFilters, Filters.apply).disjunction
  }
}