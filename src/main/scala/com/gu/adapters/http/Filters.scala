package com.gu.adapters.http

import com.gu.entities.Errors.invalidFilters
import com.gu.entities.{All, Approved, InvalidFilters, Pending, Rejected, Status}
import org.scalatra.Params

import scalaz.Scalaz._
import scalaz.{ValidationNel, \/}

case class Filters(status: Status)

object Filters {
  def fromParams(params: Params): \/[InvalidFilters, Filters] = {
    val status: ValidationNel[String, Status] = params.get("status") match {
      case Some("approved") => Approved.success
      case Some("rejected") => Rejected.success
      case Some("pending") => Pending.success
      case Some("all") => All.success
      case Some(invalid) => s"'$invalid' is not a valid status type. Must be 'pending', 'approved', 'rejected' or 'all'.".failureNel
      case None => All.success
    }

    status.bimap(invalidFilters, Filters.apply).disjunction
  }
}