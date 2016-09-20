package com.gu.core.models

import org.joda.time.DateTime

case class Filters(
  status: Status,
  since: Option[DateTime],
  until: Option[DateTime],
  order: Option[OrderBy]
)
