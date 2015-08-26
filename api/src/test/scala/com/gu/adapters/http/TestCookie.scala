package com.gu.adapters.http

import com.gu.identity.cookie.{ GuUCookieData, GuUDecoder }
import com.gu.identity.model.User

object TestCookie {
  val userId = 654321
  val cookieData = "valid_cookie"
  val testCookie = userId -> cookieData
}

object StubGuUDecoder extends GuUDecoder(null) {
  import TestCookie._

  override def getUserDataForGuU(cookieValue: String): Option[GuUCookieData] = {
    if (cookieValue contains cookieData) {
      val user = User().copy(primaryEmailAddress = "user@test.com", id = userId.toString)
      Some(GuUCookieData(user, 0, None))
    } else
      None
  }
}