package com.gu.entities

import org.joda.time.DateTime

case class Avatar(
  id: String,
  href: String,
  avatarUrl: String,
  userId: Int,
  originalFilename: String,
  status: Status,
  createdAt: DateTime,
  lastModified: DateTime
)
