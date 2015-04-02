package com.gu.entities

import org.joda.time.DateTime

case class Avatar(
  id: String,
  avatarUrl: String,
  href: String,
  userId: Int,
  originalFilename: String,
  status: Status,
  createdAt: DateTime,
  lastModified: DateTime
)
