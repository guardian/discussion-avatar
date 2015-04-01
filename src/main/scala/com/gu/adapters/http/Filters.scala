package com.gu.adapters.http

import com.gu.entities.Errors.invalidFilters
import com.gu.entities._
import org.scalatra.Params

import scalaz._

case class Filters(status: Status)

object Filters {
  def fromParams(params: Params): \/[InvalidFilters, Filters] = {
    val status: Validation[NonEmptyList[String], Status] = params.get("status") match {
      case Some("inactive") => Success(Inactive)
      case Some("approved") => Success(Approved)
      case Some("rejected") => Success(Rejected)
      case Some("pending") => Success(Pending)
      case Some("all") => Success(All)
      case Some(invalid) => Failure(NonEmptyList(s"'$invalid' is not a valid status type. Must be 'pending', 'approved', 'rejected', 'inactive' or 'all'."))
      case None => Success(All)
    }

    status.bimap(invalidFilters, Filters.apply).disjunction
  }
}