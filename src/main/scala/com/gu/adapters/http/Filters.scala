package com.gu.adapters.http

import com.gu.entities.Errors.invalidFilters
import com.gu.entities._
import org.scalatra.Params

import scalaz._

case class Filters(status: Status)

object Filters {
  def fromParams(params: Params): \/[InvalidFilters, Filters] = {
    val status: Validation[NonEmptyList[String], Status] = params.get("status") match {
      case Some(Inactive.asString) => Success(Inactive)
      case Some(Approved.asString) => Success(Approved)
      case Some(Rejected.asString) => Success(Rejected)
      case Some(Pending.asString) => Success(Pending)
      case Some(All.asString) => Success(All)
      case Some(invalid) => Failure(NonEmptyList(s"'$invalid' is not a valid status type. Must be '${Inactive.asString}', '${Approved.asString}', '${Rejected.asString}', '${Pending.asString}' or '${All.asString}'."))
      case None => Success(All)
    }

    status.bimap(invalidFilters, Filters.apply).disjunction
  }
}