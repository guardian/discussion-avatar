package com.gu.adapters.http

import java.util.UUID

import com.gu.core.Status

sealed trait RequestParam
case class StatusRequest(status: Status)
case class AvatarRequest(url: String)
