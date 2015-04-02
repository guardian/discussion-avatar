package com.gu.entities

sealed trait Status { def asString: String }
case object All extends Status { val asString = "all" }
case object Inactive extends Status { val asString = "inactive" }
case object Approved extends Status { val asString = "approved" }
case object Rejected extends Status { val asString = "rejected" }
case object Pending extends Status { val asString = "pending" }