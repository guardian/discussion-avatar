package com.gu.core

sealed trait Status { def asString: String }
case object All extends Status { val asString = "all" }
case object Inactive extends Status { val asString = "inactive" }
case object Pending extends Status { val asString = "pending" }
case object Approved extends Status { val asString = "approved" }
case object Rejected extends Status { val asString = "rejected" }

object Status {

  def apply(status: String): Status = status match {
    case "inactive" => Inactive
    case "pending" => Pending
    case "approved" => Approved
    case "rejected" => Rejected
  }
}