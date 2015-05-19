package com.gu.adapters.http

import com.gu.core.Status

sealed trait RequestParam
case class StatusRequest(status: Status)
case class AvatarRequest(url: String)
