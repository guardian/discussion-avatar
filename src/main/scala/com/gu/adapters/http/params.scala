package com.gu.adapters.http

import com.gu.entities.Status

sealed trait RequestParam
case class StatusRequest(status: String)
case class AvatarRequest(
  userId: Int,
  originalFilename: String,
  status: Status,
  image: String // TODO will this be base64 encoded?
)
