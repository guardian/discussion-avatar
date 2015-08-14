package com.gu.adapters.http

import com.gu.core.models.Status
import org.joda.time.DateTime

sealed trait RequestParam
case class StatusRequest(status: Status)
case class AvatarRequest(url: String, isSocial: Boolean)

