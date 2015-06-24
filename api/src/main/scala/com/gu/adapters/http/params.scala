package com.gu.adapters.http

import com.gu.core.Status
import org.joda.time.DateTime

sealed trait RequestParam
case class StatusRequest(status: Status)
case class AvatarRequest(url: String, isSocial: Boolean)

case class MigratedAvatarRequest(
  userId: Int,
  image: String,
  processedImage: String,
  status: String,
  createdAt: DateTime,
  isSocial: Boolean,
  originalFilename: String
)

