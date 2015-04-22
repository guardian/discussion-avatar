package com.gu.adapters.http

import com.gu.core.Status

sealed trait RequestParam
case class StatusRequest(status: String)
case class AvatarRequest(
  userId: Int,
  originalFilename: String,
  status: Status,
  image: String // TODO will this be base64 encoded?
)
