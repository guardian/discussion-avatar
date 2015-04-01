package com.gu.entities

sealed trait Status
case object All extends Status
case object Inactive extends Status
case object Approved extends Status
case object Rejected extends Status
case object Pending extends Status