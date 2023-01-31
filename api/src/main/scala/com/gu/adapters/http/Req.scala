package com.gu.adapters.http

import com.gu.core.models.{Approved, Filters, Descending}

case class Req(base: String, path: String, filters: Filters = Filters(Approved, None, None, None))

object Req {
  def toString(req: Req): String = {
    val queryString = Filter.queryString(req.filters)
    req.base + req.path + queryString
  }
}