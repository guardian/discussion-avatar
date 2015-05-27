package com.gu.adapters.http

import java.util.UUID

import com.gu.adapters.utils.ISODateFormatter
import com.gu.core.{ Avatar, Config }

object Links {

  val pageSize = Config.pageSize

  def links(avatars: List[Avatar], url: Req, hasMore: Boolean): List[Link] = {
    val cursor = avatars.lift(pageSize - 1).map(_.lastModified)
    val first = avatars.headOption.map(_.lastModified)
    val query = url.filters

    val next = for (c <- cursor if hasMore) yield {
      val fs = query.copy(since = Some(c), until = None)
      Link("next", s"${url.base}${url.path}${Filters.queryString(fs)}")
    }

    val prev = for (f <- first if query.since.isDefined || query.until.isDefined) yield {
      val fs = query.copy(until = Some(f), since = None)
      Link("prev", s"${url.base}${url.path}${Filters.queryString(fs)}")
    }

    List(prev, next).flatten
  }
}
