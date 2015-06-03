package com.gu.core

import org.joda.time.DateTime

case class Avatar(
  id: String,
  avatarUrl: String,
  userId: Int,
  originalFilename: String,
  rawUrl: String,
  status: Status,
  createdAt: DateTime,
  lastModified: DateTime,
  isSocial: Boolean,
  isActive: Boolean
)
