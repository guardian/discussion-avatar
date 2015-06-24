package com.gu.adapters.http

import com.gu.core.Approved

case class Req(base: String, path: String, filters: Filters = Filters(Approved, None, None))

object Req {
  def toString(req: Req): String = {
    val queryString = Filters.queryString(req.filters)
    req.base + req.path + queryString
  }
}