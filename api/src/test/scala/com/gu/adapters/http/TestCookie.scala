package com.gu.adapters.http

import com.gu.identity.cookie.GuUDecoder
import com.gu.identity.model.User

object TestCookie {
  val userId = "654321"
  val fakeScguu = "valid-sc-gu-u"
  val testSecureCookie = userId -> fakeScguu
}
