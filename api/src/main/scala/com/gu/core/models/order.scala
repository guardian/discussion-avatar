package com.gu.core.models

sealed trait OrderBy { def asString: String }
case object Ascending extends OrderBy { val asString = "asc" }
case object Descending extends OrderBy { val asString = "desc" }

object OrderBy {

  def apply(order: String): OrderBy = order match {
    case "asc" => Ascending
    case "desc" => Descending
  }
}
