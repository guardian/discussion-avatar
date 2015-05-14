package com.gu.adapters.http

import com.gu.core.Status
import org.joda.time.DateTime

sealed trait RequestParam
case class StatusRequest(status: Status)
case class AvatarRequest(
  userId: Int,
  originalFilename: String,
  status: Status,
  image: String // TODO will this be base64 encoded?
)

case class MigratedAvatarRequest(
  userId: Int,
  image: String,
  processedImage: String,
  createdAt: DateTime,
  isSocial: Boolean,
  originalFilename: String
)
