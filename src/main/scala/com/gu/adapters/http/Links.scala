package com.gu.adapters.http

import java.util.UUID

import com.gu.core.{Avatar, Config}

object Links {

  val pageSize = Config.pageSize

  def links(avatars: List[Avatar], url: Req, hasMore: Boolean): List[Link] = {
    val cursor = avatars.lift(pageSize-1).map(_.id)
    val first = avatars.headOption.map(_.id)
    val query = url.filters

    val next = for (c <- cursor if hasMore) yield {
      val fs = query.copy(since = Some(UUID.fromString(c)), until = None)
      Link("next", s"${url.base}${url.path}${Filters.queryString(fs)}")
    }

    val prev = for (f <- first if query.since.isDefined || query.until.isDefined) yield {
      val fs = query.copy(until = Some(UUID.fromString(f)), since = None)
      Link("next", s"${url.base}${url.path}${Filters.queryString(fs)}")
    }

    List(prev, next).flatten
  }
}
